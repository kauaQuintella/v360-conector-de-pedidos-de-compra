# V360 — Conector de Pedidos de Compra
## Decisões de arquitetura e design + sequência para o Kanban

Documento de referência para consulta durante o desenvolvimento e para defesa das decisões na entrevista. Consolida tudo que foi decidido até agora. Pontos marcados como **[EM ABERTO]** ainda precisam ser fechados antes ou durante a implementação correspondente.

---

## 1. Arquitetura geral

### 1.1 Estilo
Arquitetura em camadas, com a ingestão organizada como **Pipes and Filters**, orquestrada pelos padrões **Strategy** e, secundariamente, **Factory**.

**Fluxo de ponta a ponta:**

```
Alfa (JSON aninhado)     Beta (CSV 2 arquivos)     Gama (JSON achatado)
        |                        |                          |
   POST /ingest/alfa      POST /ingest/beta         POST /ingest/gama
        |                        |                          |
  AlfaIngestor             BetaIngestor               GamaIngestor        <- Strategy
        |                        |                          |
        +------------------------+--------------------------+
                                  |
                 Normalizadores compartilhados
        (CnpjSanitizer, StatusMapper, DateParser, UnitConverter)
                                  |
                            PedidoService
                (upsert por numero_pedido_origem + cliente_origem)
                                  |
                              PostgreSQL
        (Fornecedor, Pedido, Item, Conferencia, Divergencia)
                                  |
        +-------------------------+-------------------------+
        |                         |                         |
    Consulta               Conferência                Relatório
  (pedidos + filtros)     (nota vs. pedido)        (contagens agregadas)
```

### 1.2 Camadas e responsabilidades

| Camada | Responsabilidade | Não faz |
|---|---|---|
| Controller | Validação de payload (Bean Validation), roteamento pro service | Lógica de negócio |
| DTO de entrada (por cliente) | Espelha o formato bruto de cada cliente | Normalização |
| Ingestor (Strategy) | Traduz DTO bruto → entidade de domínio, usando os normalizadores | Persistência direta |
| Normalizadores compartilhados | CNPJ, status, data, conversão de unidade | Lógica específica de um cliente só |
| Service | Upsert, regras de conferência | Acesso SQL direto |
| Repository (Spring Data JPA) | Acesso a dados | Lógica de negócio |
| Entidade / Postgres | Persistência | — |

### 1.3 Sobre os padrões

- **Strategy**: cada cliente tem um `Ingestor` (`AlfaIngestor`, `BetaIngestor`, `GamaIngestor`) implementando uma interface comum `PedidoIngestor`. Adicionar o Gama não exigiu tocar em Alfa nem Beta — isso é o Open/Closed Principle na prática, e é o ponto mais fácil de defender na entrevista quando perguntarem "o que mudou entre a Parte 1 e a Parte 2".
- **Factory**: como foi decidido ter uma rota exclusiva por cliente (`/ingest/alfa`, `/ingest/beta`, `/ingest/gama`) em vez de uma rota genérica (`/ingest/{cliente}`), o próprio Spring já resolve qual `Ingestor` usar via injeção de dependência no controller correspondente. O Factory formal fica dispensável nesse desenho — vale mencionar isso como uma simplificação consciente, não como um padrão que "faltou implementar".

---

## 2. Modelo de dados único (contrato JSON)

```json
{
  "id_pedido": "uuid",
  "numero_pedido": "GL-778",
  "cliente_origem": "GAMA",
  "data_criacao": "2026-08-15T00:00:00Z",
  "status": "OPEN",
  "moeda": "BRL",
  "fornecedor": {
    "id_fornecedor": "uuid",
    "cnpj": "34567890000112",
    "nome": "Transportes Ideal ME"
  },
  "itens": [
    {
      "id_item": "uuid",
      "linha": "1",
      "codigo_material": "TRP-01",
      "descricao": "Pallet de madeira",
      "unidade_medida": "UN",
      "quantidade_pedida": 120,
      "quantidade_recebida": 24,
      "quantidade_pendente": 96,
      "preco_unitario": 100.00
    }
  ]
}
```

### Decisões de normalização

- **CNPJ**: sempre sem máscara (só dígitos) no contrato único, independente de como o cliente envia. Sanitização centralizada num `CnpjSanitizer` usado por todos os `Ingestor`s.
- **Status**: enum único `{ OPEN, CLOSED, BLOCKED }`. Cada cliente tem seu próprio vocabulário de origem (`open/closed/blocked` no Alfa, `"EM ABERTO"/"BLOQUEADO"` no Beta, códigos `1/2/3` no Gama) — a tradução é responsabilidade de um `StatusMapper` por cliente, mas a saída é sempre o mesmo enum.
- **Unidade de medida**: o contrato único guarda sempre a **unidade em que a nota fiscal informa** (nunca a unidade de compra do cliente, como caixa). Isso é uma decisão de normalização deliberada, não um detalhe específico do Gama — deve estar no README como regra de negócio, porque é exatamente o tipo de decisão que a entrevista vai pedir para defender.
- **`quantidade_pendente`**: ver seção 3.4.

---

## 3. Schema relacional

```sql
CREATE TABLE Fornecedor (
                            id_fornecedor UUID PRIMARY KEY,
                            cnpj VARCHAR(255) UNIQUE,
                            nome VARCHAR(255)
);

CREATE TABLE Pedido (
                        id_pedido UUID PRIMARY KEY,
                        id_fornecedor UUID REFERENCES Fornecedor(id_fornecedor),
                        numero_pedido_origem VARCHAR(255),
                        cliente_origem VARCHAR(255),
                        data_criacao TIMESTAMPTZ,
                        data_ingestao TIMESTAMPTZ,
                        status VARCHAR(50),
                        moeda VARCHAR(3)
);

CREATE TABLE Item (
                      id_item UUID PRIMARY KEY,
                      id_pedido UUID REFERENCES Pedido(id_pedido),
                      linha VARCHAR(50),
                      codigo_material VARCHAR(100),
                      descricao TEXT,
                      unidade_medida VARCHAR(20),
                      quantidade_pedida NUMERIC(15,4),
                      quantidade_recebida NUMERIC(15,4),
                      quantidade_pendente NUMERIC(15,4) GENERATED ALWAYS AS (quantidade_pedida - quantidade_recebida) STORED,
                      preco_unitario NUMERIC(15,4)
    -- a respeito de quantidade_pendente, ver 3.4 
);

CREATE TABLE Conferencia (
                             id_conferencia UUID PRIMARY KEY,
                             id_pedido UUID REFERENCES Pedido(id_pedido), -- pode ser NULL se pedido não encontrado
                             fornecedor_cnpj VARCHAR(20),
                             resultado VARCHAR(20), -- APROVADA / REJEITADA
                             data_conferencia TIMESTAMPTZ
);

CREATE TABLE Divergencia (
                             id_divergencia UUID PRIMARY KEY,
                             id_conferencia UUID REFERENCES Conferencia(id_conferencia),
                             tipo VARCHAR(50), -- PEDIDO_NAO_ENCONTRADO, FORNECEDOR_DIVERGENTE,
    -- MATERIAL_NAO_ENCONTRADO, QUANTIDADE_EXCEDE_PENDENTE,
    -- VALOR_DIVERGENTE, PEDIDO_BLOQUEADO, PEDIDO_ENCERRADO
                             codigo_material VARCHAR(100),
                             valor_esperado TEXT,
                             valor_recebido TEXT,
                             descricao TEXT
);

ALTER TABLE Pedido ADD CONSTRAINT uk_pedido_origem UNIQUE (numero_pedido_origem, cliente_origem);
ALTER TABLE Item ADD CONSTRAINT uk_item_pedido UNIQUE (id_pedido, linha);

-- Índices para os filtros do requisito de consulta
CREATE INDEX idx_pedido_cliente_origem ON Pedido(cliente_origem);
CREATE INDEX idx_pedido_status ON Pedido(status);
CREATE INDEX idx_pedido_fornecedor ON Pedido(id_fornecedor);
```

### 3.1 Por que `Conferencia`/`Divergencia` existem
O requisito de relatório ("quantas notas passaram, quantas travaram e por quais motivos") exige histórico. Sem essas tabelas não há dado para agregar — foi uma lacuna identificada e corrigida antes da implementação.

### 3.2 Fornecedor único entre clientes
`Fornecedor.cnpj` tem `UNIQUE` global: um fornecedor que aparece em dois clientes diferentes (ex.: mesmo CNPJ no Alfa e no Gama) é tratado como a mesma entidade no ecossistema. Decisão consciente — documentar no README, porque não é óbvia à primeira vista.

### 3.3 Tipagem financeira
`NUMERIC(15,4)` em todos os campos monetários e de quantidade, evitando erro de arredondamento de ponto flutuante — crítico porque o Gama trabalha com centavos e fator de conversão, cenário onde erro de float aparece rápido.

### 3.4 `quantidade_pendente`: coluna gerada pelo banco — decisão fechada
**Decisão:** coluna `GENERATED ALWAYS AS (quantidade_pedida - quantidade_recebida) STORED` no Postgres.

**Por que não persistir estaticamente:** toda reingestão que atualiza `quantidade_recebida` precisaria recalcular e regravar `quantidade_pendente` manualmente — motivo de bug silencioso se esquecido em qualquer ponto do fluxo de upsert.

**Por que coluna gerada e não cálculo no service/DTO:** o valor fica consistente diretamente no banco, independente de qual camada lê o dado. Qualquer query que retorne `Item` já traz o valor correto sem precisar de lógica adicional em Java. Só é viável porque o projeto usa Postgres desde o início (ver seção 4.1) — não funcionaria com H2.

**Implicação para o mapeamento JPA:** a entidade `Item` deve mapear o campo como:
```java
@Column(name = "quantidade_pendente", insertable = false, updatable = false)
private BigDecimal quantidadePendente;
```
`insertable = false, updatable = false` instrui o JPA a nunca tentar escrever nesse campo — o Postgres é quem calcula. Tentar inserir ou atualizar uma coluna `GENERATED ALWAYS` pelo JPA causa erro no banco.

### 3.5 Upsert
Chave de upsert: `(numero_pedido_origem, cliente_origem)`. Implementado via JPA (`findBy...` seguido de save/update em código), não via SQL nativo (`INSERT ... ON CONFLICT`) — decisão ligada à escolha de banco, ver seção 4.2.

**[EM ABERTO]** O que fazer quando um item desaparece numa reingestão (pedido reenviado com um item a menos)? Deletar o órfão, marcar como inativo, ou ignorar. Decidir e registrar no README.

---

## 4. Estratégia de persistência e ambiente

### 4.1 Postgres desde o dia zero
Descartada a ideia de começar com H2 e migrar depois. Motivo: o `docker-compose` com Postgres já está planejado desde o README inicial, e manter H2 como padrão de desenvolvimento criaria dois ambientes divergentes bem no ponto onde mais importa que não divirjam — upsert e colunas computadas.

### 4.2 Upsert via JPA, não SQL nativo
`INSERT ... ON CONFLICT DO UPDATE` é sintaxe específica do Postgres; H2 não garante comportamento idêntico. Como o projeto já usa Postgres em todos os ambientes, isso deixou de ser um bloqueio técnico, mas o upsert via JPA (find + save) ainda foi preferido por ser mais simples de testar e depurar do que SQL nativo.

### 4.3 Onde H2 ainda serve
Testes unitários que mockam o repository (não testam upsert real nem colunas computadas). Não usar H2 para testes de integração de persistência.

---

## 5. Regras de negócio da conferência de nota fiscal

### 5.1 Casamento nota ↔ pedido
Por `numero_pedido` (chave do pedido) + `codigo_material` (item a item).

### 5.2 Tipos de divergência definidos
| Tipo | Quando ocorre |
|---|---|
| `PEDIDO_NAO_ENCONTRADO` | Número do pedido da nota não existe na base |
| `FORNECEDOR_DIVERGENTE` | CNPJ da nota ≠ CNPJ do pedido |
| `MATERIAL_NAO_ENCONTRADO` | Material da nota não está nos itens do pedido |
| `QUANTIDADE_EXCEDE_PENDENTE` | Quantidade da nota > quantidade pendente do item |
| `VALOR_DIVERGENTE` | Valor total do item não bate com `quantidade × preço_unitário` |
| `PEDIDO_BLOQUEADO` | Pedido está com status `BLOCKED` |
| `PEDIDO_ENCERRADO` | Pedido está com status `CLOSED` |

### 5.3 **[EM ABERTO]** — decidir antes de codar a conferência
- **Tolerância de preço**: a nota informa valor total do item, não unitário — a comparação exige calcular `quantidade × preco_unitario` e aceitar uma margem (ex.: R$0,01) por causa de arredondamento na conversão de unidade do Gama. Definir o valor da margem.
- **Pedido `BLOCKED`/`CLOSED`**: reprovação automática da nota inteira, ou é só mais um tipo de divergência que aparece ao lado de outras? Decidir e justificar.
- **Múltiplas linhas com mesmo material no mesmo pedido**: como casar a nota quando isso acontece? Pela primeira linha com saldo disponível? Somando as pendências de todas as linhas daquele material?

Essas três decisões são as que mais peso têm na entrevista — o desafio pede explicitamente para "decidir, registrar no README por que decidiu assim e estar pronto para defender".

---

## 6. Particularidades por cliente (ingestão)

| Cliente | Formato | Conversões específicas |
|---|---|---|
| Alfa | JSON aninhado | Data já em ISO, nenhuma conversão de data necessária |
| Beta | CSV (2 arquivos, `;`, padrão BR) | Número BR (`1.200,000` → remover `.` de milhar, trocar `,` por `.`), data `dd/MM/yyyy`, CNPJ mascarado, status em português, join cabeçalho+itens por `NUMERO_PEDIDO` |
| Gama | JSON achatado (uma linha por item) | Agrupar linhas por `ped`; `dt_criacao` epoch segundos → `Instant`; `preco_unit_centavos` ÷ 100; status numérico (1/2/3); conversão de unidade de compra → unidade de venda via `fator_conv` (`quantidade_pedida = qtd_ped × fator_conv`; `preco_unitario = (preco_unit_centavos/100) ÷ fator_conv`) |

**O que foi só adicionar (Parte 2) vs. o que exigiu mexer no que já existia**: preencher esta linha no README depois de implementar o Gama — é um dos pontos que o desafio pede explicitamente.

---

## 7. Sequência de execução — cards para o Kanban

Board sugerido: colunas `Backlog → To Do → In Progress → Review/Testes → Done`, campo extra `Dia Alvo` para agrupar por dia, labels por épico (`ingestao-alfa`, `ingestao-beta`, `ingestao-gama`, `consulta`, `conferencia`, `relatorio`, `infra`, `docs`).

## 8. Checklist de pontos a não esquecer
- Anotar prompts no `AI_USAGE.md` **no dia em que acontecem**, não reconstruir de memória no fim.
- `git tag parte-1` é pedido explicitamente no enunciado — não esquecer.
- Prioridade em caso de atraso: as três funcionalidades centrais (consulta, conferência, relatório) e a clareza das regras de negócio no README nunca são cortadas. Cortar antes: testes extensos → paginação → vídeo vira prints → Docker vira "rodar local".
