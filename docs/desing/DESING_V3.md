# V360 — Conector de Pedidos de Compra
## Cópia de trabalho do design (alinhada ao que falta fazer)

Este arquivo é a **cópia operacional** do `DESING.md`. Foi ajustada com base na [análise estrutural do projeto](../development/ANALISE_ESTRUTURAL_PROJETO.md) (06/09/2026): corrige trechos desatualizados, marca o que já está no código e deixa explícito o que ainda precisa ser implementado.

**Atualização de 08/09/2026:** sincronizada com [`docs/FLUXO_LOGICO_IMPLEMENTACAO.md`](../development/FLUXO_LOGICO_IMPLEMENTACAO.md), que auditou o `src/` e constatou que a Parte 1 (Alfa + Beta + consulta + conferência + relatório) está implementada. Apenas Gama (Parte 2) e a tag `parte-1` restam.

As quatro decisões de negócio que estavam em aberto (**tolerância de preço**, **BLOCKED/CLOSED**, **material duplicado**, **itens órfãos**) estão **fechadas** nas seções 3.5 e 5.3 e **implementadas no código**. Pontos **[FEITO]** não devem ser refeitos; devem ser reutilizados.

---

## 0. Payloads de exemplo dos clientes (`public/`)

Os **modelos reais de payload** usados para desenhar DTOs, ingestores e testes **não estão neste documento**. Eles vivem em `public/`, nomeados pela empresa. Use esses arquivos como fonte da verdade do formato bruto — o DTO de cada cliente deve espelhá-los, não o contrato JSON unificado da seção 2.

| Empresa | Formato | Arquivo(s) | Como usar |
|---|---|---|---|
| **Alfa Energia** | JSON aninhado (lote em `purchase_orders`) | [`public/alfaEnergiaPayload.json`](../../public/alfaEnergiaPayload.json) | Corpo de `POST /ingest/alfa`. Coberto por `AlfaPayloadDTO` / `AlfaIngestor`. **[FEITO]** |
| **Beta Alimentos** | CSV (2 arquivos, `;`, padrão BR) | [`public/betaAlimentos/cabecalho.csv`](../../public/betaAlimentos/cabecalho.csv) e [`public/betaAlimentos/itens.csv`](../../public/betaAlimentos/itens.csv) | Entrada de `POST /ingest/beta`. Join por `NUMERO_PEDIDO`. **[FEITO]** |
| **Gama Logística** | JSON achatado (array: 1 objeto = 1 linha de item) | [`public/gamaLogisticaPayload.json`](../../public/gamaLogisticaPayload.json) | Corpo de `POST /ingest/gama`. Agrupar por `ped`. **Ainda não implementado.** |

Detalhes visíveis nos arquivos (não inventar campos):

- **Alfa** (`alfaEnergiaPayload.json`): `created_at` é data civil `"2026-08-05"` (`YYYY-MM-DD`), **não** um `Instant` ISO com horário. O nome do fornecedor pode vir com `\n` (`"Metalúrgica São Jorge\nS.A."`).
- **Beta** (`betaAlimentos/`): cabeçalho com CNPJ mascarado, emissão `dd/MM/yyyy`, situação em português (pode quebrar linha no CSV); itens com número BR (`1.200,000`, `6,49`). Os CSVs **não são RFC 4180** (sem aspas; `\n` no meio do campo). Tratado por `BetaCsvRecordAssembler` (máquina de estados + lookahead por contagem de `;`).
- **Gama** (`gamaLogisticaPayload.json`): `dt_criacao` em epoch segundos; `preco_unit_centavos`; `situacao` numérica; `fator_conv` + unidade de compra (`CX`).

---

## 1. Arquitetura geral

### 1.1 Estilo
Arquitetura em camadas, ingestão como **Pipes and Filters**, orquestrada por **Strategy**. **Factory formal dispensada**: há uma rota por cliente; o Spring injeta o ingestor no controller correspondente.

**Fluxo atual (ponta a ponta):**

```
Alfa (JSON aninhado)     Beta (CSV 2 arquivos)     Gama (JSON achatado)
        |                        |                          |
   POST /ingest/alfa      POST /ingest/beta         POST /ingest/gama
        |                        |                          |
    AlfaIngestor       BetaCsvParser → BetaIngestor     GamaIngestor        <- Strategy
     [FEITO]                  [FEITO]                  [A FAZER]
        |                        |                          |
        +------------------------+--------------------------+
                                  |
                 Normalizadores (ver 1.4)
                                  |
                            PedidoService  [FEITO — upsert + órfãos]
                (upsert por numero_pedido_origem + cliente_origem)
                                  |
                              PostgreSQL
        (Fornecedor, Pedido, Item  [FEITO]
         Conferencia, Divergencia  [FEITO — DDL + JPA + lógica])
                                  |
        +-------------------------+-------------------------+
        |                         |                         |
    Consulta               Conferência                Relatório
    [FEITO]                 [FEITO]                   [FEITO]
```

O caminho Alfa e Beta no código já seguem esse desenho. Gama deve **repetir o mesmo padrão** (controller + DTOs + ingestor), sem alterar Alfa ou Beta.

### 1.2 Camadas e responsabilidades

| Camada | Responsabilidade | Não faz | Estado |
|---|---|---|---|
| Controller | Validação (`@Valid`), roteamento | Lógica de negócio | Alfa e Beta **feitos**; Gama **a fazer** |
| DTO de entrada (por cliente) | Espelha o payload bruto em `public/` | Normalização | `dto.alfa` e `dto.beta` **feitos** |
| Ingestor (Strategy) | DTO bruto → entidade de domínio | Persistência | `AlfaIngestor` e `BetaIngestor` **feitos** |
| Parser (Beta) | Remontagem + OpenCSV + join | Persistência | `BetaCsvRecordAssembler`, `BetaCsvParser` **feitos** |
| DTO de saída | Contrato unificado snake_case | — | `dto.pedido`, `ConferenciaResponseDTO`, `RelatorioConferenciasDTO`, `ErroRespostaDTO` **feitos** |
| Normalizadores | CNPJ, status, data, string, unidade | Persistência | Alfa + Beta completos — ver 1.4 |
| Service | Upsert, consulta, conferência | SQL nativo | `PedidoService`, `PedidoConsultaService`, `ConferenciaService` **feitos** |
| Repository | Acesso a dados | Lógica de negócio | Todos os repositórios (incluindo `Conferencia`/`Divergencia`) **feitos** |
| Entidade / Postgres | Persistência | — | Todas as 5 entidades **feitas** |

### 1.3 Padrões

- **Strategy**: interface `PedidoIngestor<T>` já existe. `AlfaIngestor` e `BetaIngestor` **feitos**. **A fazer:** `GamaIngestor`.
- **Open/Closed**: Beta não alterou classes do Alfa. O mesmo deve valer para Gama.
- **Factory**: não implementar. Rotas dedicadas `/ingest/alfa`, `/ingest/beta`, `/ingest/gama`.
- **Pipes and Filters**: bruto → (parser) → DTO → ingestor/normalizadores → entidade → upsert.

### 1.4 Normalizadores — o que reutilizar vs. o que criar

| Peça | Estado | Uso |
|---|---|---|
| `CnpjSanitizer` | **Feito** (estático, compartilhado) | Alfa e Beta já usam; Gama **deve reutilizar** |
| `StringSanitizer` | **Feito** (não estava no design original; surgiu do `\n` no payload Alfa) | Reutilizar em nomes/descrições do Gama |
| `StatusMapper` (interface) + `AlfaStatusMapper` | **Feito** | — |
| `BetaStatusMapper` | **Feito** — `EM ABERTO` → `OPEN`, `BLOQUEADO` → `BLOCKED`, `ENCERRADO` → `CLOSED` | Não reimplementar |
| Mapper Gama (1/2/3) | **A fazer** | Status numérico do Gama |
| `AlfaDateParser` | **Feito** — `LocalDate` `YYYY-MM-DD` → `Instant` (`America/Sao_Paulo`) | Específico do Alfa. |
| `BetaDateParser` | **Feito** — `dd/MM/yyyy` → `Instant` | Específico do Beta. |
| `BetaNumberParser` | **Feito** — número BR (`1.200,000` / `6,49`) → `BigDecimal` | Específico do Beta. |
| Parser epoch (Gama) | **A fazer** | `dt_criacao` epoch segundos → `Instant`. Manter isolado (sem interface genérica `DateParser` — formatos incompatíveis). |
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

> **Atenção:** a resposta do `POST /ingest/*` devolve `numero_pedido_origem`; a consulta `GET /pedidos/{id}` devolve `numero_pedido`. São o mesmo dado com nomes ligeiramente diferentes — unificar no README.

### Decisões de normalização (já vigentes no Alfa e Beta; aplicar iguais no Gama)

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

**[FEITO]:** entidades JPA `Conferencia` e `Divergencia` + repositórios + service/endpoints. Todas as tabelas sobem com a aplicação.

> **Atenção:** `Conferencia.fornecedor_cnpj` é `VARCHAR(20)` enquanto `Fornecedor.cnpj` é `VARCHAR(255)`. Após sanitizar cabe em 14 dígitos, mas os tipos DDL diferem. Não é bug no happy path; documentar na entrevista.

### 3.1 Por que `Conferencia`/`Divergencia` existem
O relatório ("quantas notas passaram, quantas travaram e por quais motivos") precisa de histórico. Sem persistir conferências não há o que agregar.

### 3.2 Fornecedor único entre clientes
`Fornecedor.cnpj` é `UNIQUE` global. O mesmo CNPJ no Alfa, Beta e futuro Gama é a mesma entidade. O `PedidoService` já faz upsert por CNPJ (atualiza nome se existir). Documentar no README.

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

> **Atenção:** os testes unitários da conferência setam `quantidadePendente` diretamente no objeto. Em produção o valor só existe após leitura do Postgres. Não há suíte de integração que prove o cruzamento com a coluna `GENERATED` — risco citado no README como "o que faria diferente".

### 3.5 Upsert — **[FEITO]**
Chave: `(numero_pedido_origem, cliente_origem)`. Implementado em `PedidoService` via JPA (`findBy...` + save), **não** SQL nativo.

Fluxo real (reutilizar, não reescrever):

1. Cópia defensiva da lista de itens **antes** de qualquer `.clear()` (regressão corrigida do Alfa).
2. `resolverFornecedor`: `findByCnpj` → atualiza nome ou cria.
3. `resolverPedido`: `findByNumeroPedidoOrigemAndClienteOrigem` → atualiza ou cria.
4. `resolverItens`: `findByPedidoAndLinha` → atualiza ou cria.
5. **Itens órfãos** — ver 3.5.1 (**implementado** em `removerOrfaos`).

> **Atenção:** `orphanRemoval = true` ainda está no mapeamento de `Pedido`. O design pede delete **explícito e condicional** (regra no `PedidoService`). O comportamento desejado prevalece enquanto o upsert não fizer `.clear()` na coleção — mas a anotação é armadilha em futuras refatorações.

### 3.5.1 Decisão 4 — Hard delete condicional para itens órfãos — **[FEITO]**

**Regra:** na reingestão, depois de upsertar as linhas que vieram no arquivo, o `PedidoService` compara o conjunto persistido com o conjunto do payload. Se uma linha (`id_pedido` + `linha`) existia no banco e **sumiu** do arquivo novo, o código **consulta o histórico** daquele item antes de apagar.

| Histórico no banco | Ação |
|---|---|
| `quantidade_recebida = 0` **e** o item **não** aparece em `Divergencia` de conferências daquele pedido (nenhum recebimento fiscal já cruzado com essa linha/material naquela conferência persistida) | **Hard delete** — remove o registro. Era lixo de um envio anterior; deixar órfão geraria pendência fantasma na conferência. |
| `quantidade_recebida > 0` **ou** já existe divergência/conferência apontando aquele item | **Preservar** — não apaga. O arquivo novo não pode apagar rastro operacional já movimentado. |

Implementado em `removerOrfaos` no `PedidoService`. O veto de órfão olha **recebimento + divergência do material naquele pedido**, não "qualquer conferência do pedido" (uma conferência só de `FORNECEDOR_DIVERGENTE` não congela todas as linhas).

Não usar soft-delete (`inativo`). Não apagar cegamente todos os órfãos (`orphanRemoval` irrestrito). Na implementação, o delete é **explícito e condicional** no `PedidoService` (após o passo 4), nunca um efeito colateral do `.clear()` na coleção.

**Argumento técnico:** o upsert precisa refletir o arquivo vigente (senão a conferência soma `quantidade_pendente` de linhas que o cliente já retirou do pedido), mas um delete incondicional apagaria histórico de recebimento. Olhar o banco — recebimento e conferências — separa "linha cancelada sem movimento" de "linha que já entrou no fluxo fiscal".

---

## 4. Estratégia de persistência e ambiente

### 4.1 Postgres desde o dia zero — **[FEITO]**
`compose.yaml` (Postgres 17 + app + pgAdmin), `application.properties` apontando para Postgres.

> **Atenção:** `pom.xml` traz H2 (`runtime`) e `spring-boot-h2console`. README ainda menciona H2 como padrão local — isso está **incorreto**. A aplicação só usa Postgres via variáveis de ambiente definidas no `compose.yaml`. Corrigir no README (DIA6).

### 4.2 Upsert via JPA — **[FEITO]**
Preferir find + save a `INSERT ... ON CONFLICT`.

### 4.3 H2
Só (se necessário) para testes que **não** exercitam upsert real nem coluna gerada. Não usar H2 como banco de integração de persistência. Testes atuais do Alfa e Beta são unitários puros (`new` / Mockito), sem Spring Context.

---

## 5. Regras de negócio da conferência de nota fiscal

**Status: [FEITO]** — tabelas SQL, entidades JPA, `ConferenciaService` e endpoints implementados com as decisões 5.3 já fechadas.

O `ConferenciaService` **acumula** divergências numa lista e só ao final persiste `Conferencia` (`APROVADA` se a lista estiver vazia; `REJEITADA` caso contrário) + uma linha em `Divergencia` por falha. Não faz `return` antecipado no meio da avaliação.

> **Atenção:** a conferência **não atualiza** `quantidade_recebida`. O saldo pendente vem da última ingestão (coluna gerada `pedida − recebida`). A nota fiscal é um cruzamento de conferência, não um evento de recebimento físico. Deixar claro na entrevista: **fonte de recebimento = ERP (reingestão), não a NF da V360**.

> **Atenção técnica:** `findByNumeroPedidoOrigemAndClienteOrigem` na conferência não faz `JOIN FETCH` de itens/fornecedor. Funciona dentro de `@Transactional` (lazy load), mas é N+1 e depende da sessão aberta. A consulta de detalhe já usa `JOIN FETCH`; a conferência não replicou isso.

### 5.1 Casamento nota ↔ pedido
Por `(numero_pedido, cliente_origem)` (localizar o pedido) e, nos itens, por **`codigo_material` agregado** (decisão 3) — não por FIFO linha a linha. A nota obriga `cliente_origem` para não casar com o pedido errado quando dois ERPs repetem o número.

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

### 5.3 Decisões de conferência — **fechadas e implementadas**

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

### 5.4 Endpoints implementados
- `POST /notas-fiscais/conferir` — **[FEITO]**
- `GET /relatorios/conferencias` — **[FEITO]** (contagem bruta do histórico; reprocessar a mesma nota infla totais — sem idempotência)
- `GET /pedidos` e `GET /pedidos/{id}` — **[FEITO]** (filtros: `cliente_origem`, `fornecedor` CNPJ sanitizado, `status`, `com_pendencia`; lista sem itens; detalhe com `JOIN FETCH`; vazio = 200; id inexistente = 404)

---

## 6. Particularidades por cliente (ingestão)

Fonte de formato: seção 0 (`public/`).

| Cliente | Payload | Conversões | Estado |
|---|---|---|---|
| **Alfa Energia** | [`public/alfaEnergiaPayload.json`](../../public/alfaEnergiaPayload.json) | `created_at` `LocalDate` → `Instant` (`AlfaDateParser`); CNPJ; status `open/closed/blocked`; `StringSanitizer` no nome do fornecedor | **[FEITO]** e testado (~42 unitários). Não reimplementar. |
| **Beta Alimentos** | [`public/betaAlimentos/cabecalho.csv`](../../public/betaAlimentos/cabecalho.csv) + [`public/betaAlimentos/itens.csv`](../../public/betaAlimentos/itens.csv) | `BetaCsvRecordAssembler` (máquina de estados); OpenCSV `@CsvBindByPosition`; join por `NUMERO_PEDIDO`; `BetaNumberParser` (número BR); `BetaDateParser` (`dd/MM/yyyy`); `CnpjSanitizer`; `BetaStatusMapper` | **[FEITO]** — `POST /ingest/beta` (`multipart/form-data`). Alfa não foi alterado (Open/Closed). |
| **Gama Logística** | [`public/gamaLogisticaPayload.json`](../../public/gamaLogisticaPayload.json) | Agrupar por `ped`; epoch `dt_criacao` → `Instant`; `preco_unit_centavos` ÷ 100; status 1/2/3; `quantidade_pedida = qtd_ped × fator_conv`; `preco_unitario = (preco_unit_centavos/100) ÷ fator_conv`; persistir unidade da nota | **A fazer** (`POST /ingest/gama`, DTOs, `GamaIngestor`, `UnitConverter`, testes) |

**Correção em relação ao `DESING.md` original:** não tratar a data do Alfa como "já ISO, sem conversão". O arquivo `alfaEnergiaPayload.json` envia só `YYYY-MM-DD`.

**Parte 2 (Gama):** no README, registrar o que foi só **adicionar** (ingestor/controller/DTOs) vs. o que **exigiu mexer** no que já existia. O desafio pede isso explicitamente.

> **Atenção:** erros de parse/join do Beta (item sem cabeçalho, número BR inválido, status desconhecido) lançam `IllegalArgumentException`. O `ApiExceptionHandler` só trata `ResponseStatusException` e Bean Validation — erros do Beta tendem a **500**, não 400 padronizado. Considerar handler específico se houver tempo.

---

## 7. O que falta fazer (Kanban honesto)

Estado em 08/09/2026. Alfa (DIA1) e Beta (DIA2) concluídos. Consulta, conferência e relatório (DIA3+DIA4) implementados. Não reabrir essas fatias salvo bug.

| Dia | Escopo | Status | O que de fato falta |
|---|---|---|---|
| DIA1 | Setup + ingestão Alfa | **Feito** | — |
| DIA2 | Ingestão Beta | **Feito** | — |
| DIA3 | Consultas | **Feito** | — |
| DIA4 | Conferência + relatório + tag | **Parcialmente feito** | `ConferenciaService`, entidades, endpoints feitos. Falta: `git tag parte-1` |
| DIA5 | Gama (Parte 2) | **Pendente** | DTOs a partir de `public/gamaLogisticaPayload.json`; agrupamento por `ped`; epoch; centavos; `UnitConverter`; `POST /ingest/gama`; testes |
| DIA6 | Entregáveis | **Pendente** | README fiel (remover H2 como padrão; documentar Postgres + `.env`; unificar `numero_pedido`); coleção `.http` / Postman; demo; `AI_USAGE.md` completo |
| DIA7 | Buffer | **Pendente** | Revisão |

---

## 8. Checklist de pontos a não esquecer
- Payloads oficiais: `public/alfaEnergiaPayload.json`, `public/betaAlimentos/cabecalho.csv`, `public/betaAlimentos/itens.csv`, `public/gamaLogisticaPayload.json`.
- Anotar prompts no `AI_USAGE.md` **no dia**, não de memória no fim. O arquivo atual está incompleto (cortado no meio).
- `git tag parte-1` é requisito do enunciado — ainda não criada.
- Implementar `UnitConverter` no Gama antes de qualquer conferência do Gama — `quantidade_pendente` distorcida quebraria a agregação por material.
- Conferência: tolerância **R$ 0,05**; lista completa de divergências (sem `return` em `BLOCKED`/`CLOSED`); cruzar por **soma de pendente por `codigo_material`**. Tudo isso já está implementado — não alterar.
- Testes Alfa+Beta (~129 unitários) devem continuar passando ao adicionar Gama.
- Levar as quatro decisões (§5.3) e as estratégias do `FLUXO_LOGICO_IMPLEMENTACAO.md` para a entrevista.
- Prioridade se atrasar: não cortar consulta, conferência, relatório nem regras no README. Cortar antes: testes extensos → paginação → vídeo vira prints → Docker vira "rodar local".
