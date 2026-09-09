# V360 — Conector de Pedidos de Compra
## Cópia de trabalho do design (alinhada ao que falta fazer)

Este arquivo é a **cópia operacional** do `DESING.md`. Foi ajustada com base na [análise estrutural do projeto](../development/ANALISE_ESTRUTURAL_PROJETO.md) (06/09/2026): corrige trechos desatualizados, marca o que já está no código e deixa explícito o que ainda precisa ser implementado.

As quatro decisões de negócio que estavam em aberto (**tolerância de preço**, **BLOCKED/CLOSED**, **material duplicado**, **itens órfãos**) estão **fechadas** nas seções 3.5 e 5.3. Implementar exatamente essas regras. Pontos **[FEITO]** não devem ser refeitos; devem ser reutilizados.

---

## 0. Payloads de exemplo dos clientes (`public/`)

Os **modelos reais de payload** usados para desenhar DTOs, ingestores e testes **não estão neste documento**. Eles vivem em `public/`, nomeados pela empresa. Use esses arquivos como fonte da verdade do formato bruto — o DTO de cada cliente deve espelhá-los, não o contrato JSON unificado da seção 2.

| Empresa | Formato | Arquivo(s) | Como usar |
|---|---|---|---|
| **Alfa Energia** | JSON aninhado (lote em `purchase_orders`) | [`public/alfaEnergiaPayload.json`](../../public/alfaEnergiaPayload.json) | Corpo de `POST /ingest/alfa`. Já coberto por `AlfaPayloadDTO` / `AlfaIngestor`. |
| **Beta Alimentos** | CSV (2 arquivos, `;`, padrão BR) | [`public/betaAlimentos/cabecalho.csv`](../../public/betaAlimentos/cabecalho.csv) e [`public/betaAlimentos/itens.csv`](../../public/betaAlimentos/itens.csv) | Entrada de `POST /ingest/beta`. Join por `NUMERO_PEDIDO`. **Ainda não implementado.** |
| **Gama Logística** | JSON achatado (array: 1 objeto = 1 linha de item) | [`public/gamaLogisticaPayload.json`](../../public/gamaLogisticaPayload.json) | Corpo de `POST /ingest/gama`. Agrupar por `ped`. **Ainda não implementado.** |

Detalhes visíveis nos arquivos (não inventar campos):

- **Alfa** (`alfaEnergiaPayload.json`): `created_at` é data civil `"2026-08-05"` (`YYYY-MM-DD`), **não** um `Instant` ISO com horário. O nome do fornecedor pode vir com `\n` (`"Metalúrgica São Jorge\nS.A."`).
- **Beta** (`betaAlimentos/`): cabeçalho com CNPJ mascarado, emissão `dd/MM/yyyy`, situação em português (pode quebrar linha no CSV); itens com número BR (`1.200,000`, `6,49`).
- **Gama** (`gamaLogisticaPayload.json`): `dt_criacao` em epoch segundos; `preco_unit_centavos`; `situacao` numérica; `fator_conv` + unidade de compra (`CX`).

---

## 1. Arquitetura geral

### 1.1 Estilo
Arquitetura em camadas, ingestão como **Pipes and Filters**, orquestrada por **Strategy**. **Factory formal dispensada**: há uma rota por cliente; o Spring injeta o ingestor no controller correspondente.

**Fluxo alvo (ponta a ponta):**

```
Alfa (JSON aninhado)     Beta (CSV 2 arquivos)     Gama (JSON achatado)
        |                        |                          |
   POST /ingest/alfa      POST /ingest/beta         POST /ingest/gama
        |                        |                          |
    AlfaIngestor             BetaIngestor               GamaIngestor        <- Strategy
     [FEITO]                  [A FAZER]                  [A FAZER]
        |                        |                          |
        +------------------------+--------------------------+
                                  |
                 Normalizadores (ver 1.4)
                                  |
                            PedidoService  [FEITO — upsert]
                (upsert por numero_pedido_origem + cliente_origem)
                                  |
                              PostgreSQL
        (Fornecedor, Pedido, Item  [FEITO]
         Conferencia, Divergencia  [DDL FEITO; JPA + lógica A FAZER])
                                  |
        +-------------------------+-------------------------+
        |                         |                         |
    Consulta               Conferência                Relatório
    [A FAZER]               [A FAZER]                 [A FAZER]
```

O caminho Alfa no código já segue esse desenho: `AlfaController` → `AlfaIngestor` (`PedidoIngestor<AlfaPedidoDTO>`) → `PedidoService.upsert` → Postgres. Beta e Gama devem **repetir o mesmo padrão** (controller + DTOs + ingestor), sem alterar o Alfa.

### 1.2 Camadas e responsabilidades

| Camada | Responsabilidade | Não faz | Estado |
|---|---|---|---|
| Controller | Validação (`@Valid`), roteamento | Lógica de negócio | Alfa **feito**; Beta e Gama **a fazer** |
| DTO de entrada (por cliente) | Espelha o payload bruto em `public/` | Normalização | Só `dto.alfa` **feito** |
| Ingestor (Strategy) | DTO bruto → entidade de domínio | Persistência | Só `AlfaIngestor` **feito** |
| Normalizadores | CNPJ, status, data, string, unidade | Persistência | Parcial — ver 1.4 |
| Service | Upsert; depois regras de conferência | SQL nativo | Upsert **feito**; conferência **a fazer** |
| Repository | Acesso a dados | Lógica de negócio | Fornecedor / Pedido / Item **feitos**; Conferencia / Divergencia **a fazer** |
| Entidade / Postgres | Persistência | — | 3 entidades **feitas**; Conferencia / Divergencia só no `schema.sql` |

### 1.3 Padrões

- **Strategy**: interface `PedidoIngestor<T>` já existe. **A fazer:** `BetaIngestor` e `GamaIngestor` implementando essa interface, cada um com seu tipo de DTO. Não mexer no `AlfaIngestor` para caber outro cliente (Open/Closed).
- **Factory**: não implementar. Rotas dedicadas `/ingest/alfa`, `/ingest/beta`, `/ingest/gama`.

### 1.4 Normalizadores — o que reutilizar vs. o que criar

| Peça | Estado | Uso |
|---|---|---|
| `CnpjSanitizer` | **Feito** (estático, compartilhado) | Alfa já usa; Beta e Gama **devem reutilizar** |
| `StringSanitizer` | **Feito** (não estava no design original; surgiu do `\n` no payload Alfa) | Reutilizar em nomes/descrições dos outros clientes |
| `StatusMapper` (interface) + `AlfaStatusMapper` | **Feito** | **A fazer:** `BetaStatusMapper` (pt-BR) e mapper do Gama (1/2/3) |
| `AlfaDateParser` | **Feito** — `LocalDate` `YYYY-MM-DD` → `Instant` (`America/Sao_Paulo`) | Específico do Alfa. Beta (`dd/MM/yyyy`) e Gama (epoch) precisam de parsers **próprios**. Não há interface genérica `DateParser`; não é obrigatório criá-la — os formatos são incompatíveis. Se criar a interface, justificar; se não criar, cada cliente mantém o parser isolado. |
| `UnitConverter` | **Não existe** | **A fazer no Gama**: quantidade/preço da unidade de compra → unidade da nota via `fator_conv` |

---

## 2. Modelo de dados único (contrato JSON)

Contrato **de saída** (domínio / API de consulta), não o formato de entrada. Entrada = arquivos em `public/` (seção 0).

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

Mapeamento já refletido nas entidades `Pedido`, `Fornecedor` e `Item`. **Não recriar esse modelo.**

### Decisões de normalização (já vigentes no Alfa; aplicar iguais em Beta/Gama)

- **CNPJ**: só dígitos no contrato único. Sempre via `CnpjSanitizer`.
- **Status**: enum `{ OPEN, CLOSED, BLOCKED }`. Vocabulário de origem: Alfa `open/closed/blocked`; Beta `"EM ABERTO"` / `"BLOQUEADO"` (e equivalentes no CSV); Gama `1/2/3`. Cada cliente tem o próprio mapper; a saída é o mesmo enum.
- **Unidade de medida**: persistir a **unidade em que a nota informa** (não a unidade de compra, ex. caixa). Crítico no Gama (`fator_conv`). Documentar no README.
- **`quantidade_pendente`**: coluna gerada no banco (seção 3.4). O JPA não escreve nesse campo.

---

## 3. Schema relacional

O DDL **já está** em `src/main/resources/schema.sql` (`spring.sql.init.mode=always`, `ddl-auto=validate`). Não redesenhar tabelas de Fornecedor / Pedido / Item. Recriar só se o DDL estiver errado.

Referência alinhada ao arquivo real (constraints inline, `cnpj NOT NULL`, `IF NOT EXISTS`):

```sql
CREATE TABLE IF NOT EXISTS Fornecedor (
    id_fornecedor UUID PRIMARY KEY,
    cnpj          VARCHAR(255) UNIQUE NOT NULL,
    nome          VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS Pedido (
    id_pedido            UUID PRIMARY KEY,
    id_fornecedor        UUID REFERENCES Fornecedor(id_fornecedor),
    numero_pedido_origem VARCHAR(255),
    cliente_origem       VARCHAR(255),
    data_criacao         TIMESTAMPTZ,
    data_ingestao        TIMESTAMPTZ,
    status               VARCHAR(50),
    moeda                VARCHAR(3),
    CONSTRAINT uk_pedido_origem UNIQUE (numero_pedido_origem, cliente_origem)
);

CREATE TABLE IF NOT EXISTS Item (
    id_item              UUID PRIMARY KEY,
    id_pedido            UUID REFERENCES Pedido(id_pedido),
    linha                VARCHAR(50),
    codigo_material      VARCHAR(100),
    descricao            TEXT,
    unidade_medida       VARCHAR(20),
    quantidade_pedida    NUMERIC(15, 4),
    quantidade_recebida  NUMERIC(15, 4),
    quantidade_pendente  NUMERIC(15, 4) GENERATED ALWAYS AS (quantidade_pedida - quantidade_recebida) STORED,
    preco_unitario       NUMERIC(15, 4),
    CONSTRAINT uk_item_pedido UNIQUE (id_pedido, linha)
);

CREATE TABLE IF NOT EXISTS Conferencia (
    id_conferencia   UUID PRIMARY KEY,
    id_pedido        UUID REFERENCES Pedido(id_pedido), -- pode ser NULL se pedido não encontrado
    fornecedor_cnpj  VARCHAR(20),
    resultado        VARCHAR(20), -- APROVADA / REJEITADA
    data_conferencia TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS Divergencia (
    id_divergencia   UUID PRIMARY KEY,
    id_conferencia   UUID REFERENCES Conferencia(id_conferencia),
    tipo             VARCHAR(50),
    codigo_material  VARCHAR(100),
    valor_esperado   TEXT,
    valor_recebido   TEXT,
    descricao        TEXT
);

CREATE INDEX IF NOT EXISTS idx_pedido_cliente_origem ON Pedido(cliente_origem);
CREATE INDEX IF NOT EXISTS idx_pedido_status          ON Pedido(status);
CREATE INDEX IF NOT EXISTS idx_pedido_fornecedor      ON Pedido(id_fornecedor);
```

**A fazer (não o DDL):** entidades JPA `Conferencia` e `Divergencia` + repositórios + service/endpoints. As tabelas já sobem com a aplicação.

### 3.1 Por que `Conferencia`/`Divergencia` existem
O relatório ("quantas notas passaram, quantas travaram e por quais motivos") precisa de histórico. Sem persistir conferências não há o que agregar.

### 3.2 Fornecedor único entre clientes
`Fornecedor.cnpj` é `UNIQUE` global. O mesmo CNPJ no Alfa e no Gama é a mesma entidade. O `PedidoService` já faz upsert por CNPJ (atualiza nome se existir). Documentar no README.

### 3.3 Tipagem financeira
`NUMERIC(15,4)` / `BigDecimal` — já nas entidades. Manter no Gama (centavos e `fator_conv`).

### 3.4 `quantidade_pendente`: coluna gerada — decisão fechada
**Decisão:** `GENERATED ALWAYS AS (quantidade_pedida - quantidade_recebida) STORED`.

Na entidade `Item` (já mapeado):

```java
@Column(name = "quantidade_pendente", insertable = false, updatable = false)
private BigDecimal quantidadePendente;
```

Não calcular no service na ingestão. Não deixar o JPA escrever nessa coluna.

### 3.5 Upsert
Chave: `(numero_pedido_origem, cliente_origem)`. Já implementado em `PedidoService` via JPA (`findBy...` + save), **não** SQL nativo.

Fluxo real (reutilizar, não reescrever):

1. Cópia defensiva da lista de itens **antes** de qualquer `.clear()` (regressão do Alfa).
2. `resolverFornecedor`: `findByCnpj` → atualiza nome ou cria.
3. `resolverPedido`: `findByNumeroPedidoOrigemAndClienteOrigem` → atualiza ou cria.
4. `resolverItens`: `findByPedidoAndLinha` → atualiza ou cria.
5. **Itens órfãos** — ver decisão 3.5.1 (ainda **não** está no `PedidoService`; incluir no upsert).

### 3.5.1 Decisão 4 — Hard delete condicional para itens órfãos — **fechada**

**Regra:** na reingestão, depois de upsertar as linhas que vieram no arquivo, o `PedidoService` compara o conjunto persistido com o conjunto do payload. Se uma linha (`id_pedido` + `linha`) existia no banco e **sumiu** do arquivo novo, o código **consulta o histórico** daquele item antes de apagar.

| Histórico no banco | Ação |
|---|---|
| `quantidade_recebida = 0` **e** o item **não** aparece em `Divergencia` de conferências daquele pedido (nenhum recebimento fiscal já cruzado com essa linha/material naquela conferência persistida) | **Hard delete** — remove o registro. Era lixo de um envio anterior; deixar órfão geraria pendência fantasma na conferência. |
| `quantidade_recebida > 0` **ou** já existe divergência/conferência apontando aquele item | **Preservar** — não apaga. O arquivo novo não pode apagar rastro operacional já movimentado. |

Não usar soft-delete (`inativo`). Não apagar cegamente todos os órfãos (`orphanRemoval` irrestrito). A cascata `orphanRemoval = true` no `Pedido` **não** substitui essa regra: o upsert atual resolve item a item e **não** deve disparar delete em massa. Na implementação, o delete é **explícito e condicional** no `PedidoService` (após o passo 4), nunca um efeito colateral do `.clear()` na coleção.

**Argumento técnico:** o upsert precisa refletir o arquivo vigente (senão a conferência soma `quantidade_pendente` de linhas que o cliente já retirou do pedido), mas um delete incondicional apagaria histórico de recebimento. Olhar o banco — recebimento e conferências — separa “linha cancelada sem movimento” de “linha que já entrou no fluxo fiscal”.

**Implementar no DIA2 ou no mais tardar antes do DIA4.** Documentar no README.

---

## 4. Estratégia de persistência e ambiente

### 4.1 Postgres desde o dia zero — **feito**
`compose.yaml` (Postgres + app + pgAdmin), `application.properties` apontando para Postgres.

### 4.2 Upsert via JPA — **feito**
Preferir find + save a `INSERT ... ON CONFLICT`.

### 4.3 H2
Só (se necessário) para testes que **não** exercitam upsert real nem coluna gerada. Não usar H2 como banco de integração de persistência. Testes atuais do Alfa são unitários puros (`new` / Mockito), sem Spring Context.

---

## 5. Regras de negócio da conferência de nota fiscal

**Status: a implementar** com as decisões 5.3 já fechadas. Tabelas SQL prontas; sem entidades, `ConferenciaService` nem endpoints.

O `ConferenciaService` **acumula** divergências numa lista e só ao final persiste `Conferencia` (`APROVADA` se a lista estiver vazia; `REJEITADA` caso contrário) + uma linha em `Divergencia` por falha. Não faz `return` antecipado no meio da avaliação.

### 5.1 Casamento nota ↔ pedido
Por `numero_pedido` (localizar o pedido) e, nos itens, por **`codigo_material` agregado** (decisão 3) — não por FIFO linha a linha.

### 5.2 Tipos de divergência definidos
| Tipo | Quando ocorre |
|---|---|
| `PEDIDO_NAO_ENCONTRADO` | Número do pedido da nota não existe na base |
| `FORNECEDOR_DIVERGENTE` | CNPJ da nota ≠ CNPJ do pedido |
| `MATERIAL_NAO_ENCONTRADO` | Material da nota não está nos itens do pedido |
| `QUANTIDADE_EXCEDE_PENDENTE` | Quantidade da nota > soma das `quantidade_pendente` daquele `codigo_material` |
| `VALOR_DIVERGENTE` | `\|valor_nota − (quantidade × preço_unitário)\| > R$ 0,05` (decisão 1) |
| `PEDIDO_BLOQUEADO` | Pedido está `BLOCKED` — **registra e continua** (decisão 2) |
| `PEDIDO_ENCERRADO` | Pedido está `CLOSED` — **registra e continua** (decisão 2) |

### 5.3 Decisões de conferência — **fechadas** (implementar assim)

#### Decisão 1 — Tolerância absoluta de R$ 0,05

**Regra:** a nota informa valor total do item, não unitário. O esperado é `quantidade_da_nota × preco_unitario` do pedido (no domínio já normalizado: unidade da nota, `BigDecimal`). Há **`VALOR_DIVERGENTE`** somente se:

```
| valor_informado_na_nota − (quantidade × preco_unitario) | > 0.05
```

A margem é **absoluta** (R$ 0,05), não percentual. Igualdade ou diferença ≤ 0,05 **não** gera divergência de valor.

**Argumento técnico:** no Gama Logística a ingestão converte unidade de compra (caixa) → unidade de recebimento e preço de centavos → moeda. Divisão por `fator_conv` gera dízimas e resíduo. Sem folga, a conferência reprovaria notas corretas por centavos de arredondamento. R$ 0,05 cobre o resíduo típico dessa matemática sem abrir mão de erro material.

Usar `BigDecimal` (`abs().compareTo(new BigDecimal("0.05")) > 0`). Não usar `double`.

#### Decisão 2 — Avaliação completa (evitar o efeito ioiô)

**Regra:** `BLOCKED` e `CLOSED` **não** abortam a conferência. O `ConferenciaService` empilha `PEDIDO_BLOQUEADO` ou `PEDIDO_ENCERRADO` na lista e **segue** checando fornecedor, materiais, quantidades e valores. A nota sai `REJEITADA` se houver qualquer divergência; o payload/relatório lista **todas** de uma vez.

**Argumento técnico:** um `return` imediato ao ver status gera o **efeito ioiô**: o operador corrige o bloqueio, reenvia, toma divergência de quantidade, corrige, reenvia, toma divergência de valor. Acumular falhas numa lista entrega o diagnóstico completo na primeira passagem — alinhado ao requisito de relatório por motivo.

Pedido inexistente (`PEDIDO_NAO_ENCONTRADO`) é a exceção prática: sem pedido não há itens para cruzar. Nesse caso registra só essa divergência (e fornecedor da nota, se fizer sentido) e encerra o cruzamento de linhas. Status `BLOCKED`/`CLOSED` **não** entra nessa exceção.

#### Decisão 3 — Agregação por material

**Regra:** antes de cruzar a nota, agrupar os itens do pedido por `codigo_material` e **somar** `quantidade_pendente`. A quantidade da nota compara-se com esse saldo agregado, não com uma linha isolada e não com consumo FIFO.

- Material da nota ausente no mapa agregado → `MATERIAL_NAO_ENCONTRADO`.
- Quantidade da nota > saldo agregado daquele material → `QUANTIDADE_EXCEDE_PENDENTE`.
- Preço de referência para a decisão 1: se houver mais de uma linha do mesmo material, usar o `preco_unitario` **ponderado pelo pendente** (`sum(pendente × preco) / sum(pendente)`), ou, se todos os preços daquele material forem iguais (caso típico), qualquer um deles. Não inventar FIFO.

**Argumento técnico:** FIFO por linha exige ordenação, consumo parcial e estado intermediário — complexo e fácil de divergir da pendência real quando o ERP manda o mesmo SKU em várias `linha`. Somar pendente por material é o cruzamento que a nota fiscal realmente faz (ela não cita a linha interna do pedido) e cabe num `Map<String, BigDecimal>`.

### 5.4 Endpoints previstos (ainda inexistentes)
`POST /notas-fiscais/conferir`, `GET /relatorios/conferencias`, consulta de pedidos com filtros.

---

## 6. Particularidades por cliente (ingestão)

Fonte de formato: seção 0 (`public/`).

| Cliente | Payload | Conversões | Estado |
|---|---|---|---|
| **Alfa Energia** | [`public/alfaEnergiaPayload.json`](../../public/alfaEnergiaPayload.json) | `created_at` `LocalDate` → `Instant` (`AlfaDateParser`); CNPJ; status `open/closed/blocked`; `StringSanitizer` no nome do fornecedor | **Feito e testado** (`POST /ingest/alfa`). Não reimplementar. |
| **Beta Alimentos** | [`public/betaAlimentos/cabecalho.csv`](../../public/betaAlimentos/cabecalho.csv) + [`public/betaAlimentos/itens.csv`](../../public/betaAlimentos/itens.csv) | Número BR (`1.200,000` → milhar `.` fora, `,` → `.`); data `dd/MM/yyyy`; CNPJ mascarado (`CnpjSanitizer`); status pt-BR; join por `NUMERO_PEDIDO`; OpenCSV já no `pom.xml` | **A fazer** (`POST /ingest/beta`, DTOs, `BetaIngestor`, testes) |
| **Gama Logística** | [`public/gamaLogisticaPayload.json`](../../public/gamaLogisticaPayload.json) | Agrupar por `ped`; epoch `dt_criacao` → `Instant`; `preco_unit_centavos` ÷ 100; status 1/2/3; `quantidade_pedida = qtd_ped × fator_conv`; `preco_unitario = (preco_unit_centavos/100) ÷ fator_conv`; persistir unidade da nota | **A fazer** (`POST /ingest/gama`, DTOs, `GamaIngestor`, `UnitConverter`, testes) |

**Correção em relação ao `DESING.md` original:** não tratar a data do Alfa como “já ISO, sem conversão”. O arquivo `alfaEnergiaPayload.json` envia só `YYYY-MM-DD`.

**Parte 2 (Gama):** no README, registrar o que foi só **adicionar** (ingestor/controller/DTOs) vs. o que **exigiu mexer** no que já existia. O desafio pede isso explicitamente.

---

## 7. O que falta fazer (Kanban honesto)

Alfa (DIA1) está concluído. Não reabrir ingestão Alfa salvo bug.

| Dia | Escopo | Status | O que de fato falta |
|---|---|---|---|
| DIA1 | Setup + ingestão Alfa | **Feito** | — |
| DIA2 | Ingestão Beta | **Pendente** | Parser CSV OpenCSV; join dos dois arquivos em `public/betaAlimentos/`; número BR; data BR; `BetaStatusMapper`; `POST /ingest/beta`; testes |
| DIA3 | Consultas + aplicar decisões de conferência | **Pendente** | Endpoints de consulta/filtros. Decisões 1–3 do §5.3 **já fechadas** — não reabrir; só implementar no DIA4. Incluir hard delete condicional (§3.5.1) no upsert se ainda não estiver no DIA2. |
| DIA4 | Conferência + relatório + tag | **Pendente** | Entidades JPA Conferencia/Divergencia; `ConferenciaService` com lista acumulada, tolerância R$ 0,05 e agregação por material; `POST /notas-fiscais/conferir`; `GET /relatorios/conferencias`; `git tag parte-1` |
| DIA5 | Gama (Parte 2) | **Pendente** | DTOs a partir de `public/gamaLogisticaPayload.json`; agrupamento por `ped`; epoch; centavos; `UnitConverter`; `POST /ingest/gama`; testes |
| DIA6 | Entregáveis | **Pendente** | README, `AI_USAGE.md`, demo |
| DIA7 | Buffer | **Pendente** | Revisão |

---

## 8. Checklist de pontos a não esquecer
- Payloads oficiais: `public/alfaEnergiaPayload.json`, `public/betaAlimentos/cabecalho.csv`, `public/betaAlimentos/itens.csv`, `public/gamaLogisticaPayload.json`.
- Anotar prompts no `AI_USAGE.md` **no dia**, não de memória no fim.
- `git tag parte-1` é requisito do enunciado.
- Implementar itens órfãos (§3.5.1) **antes** da conferência — órfão com pendente distorce a agregação por material.
- Conferência: tolerância **R$ 0,05**; lista completa de divergências (sem `return` em `BLOCKED`/`CLOSED`); cruzar por **soma de pendente por `codigo_material`**.
- Prioridade se atrasar: consulta, conferência, relatório e regras no README não se cortam. Cortar antes: testes extensos → paginação → vídeo vira prints → Docker vira “rodar local”.
- Testes Alfa (~42 unitários) devem continuar passando ao adicionar Beta/Gama. Levar as quatro decisões para o README na entrevista.
