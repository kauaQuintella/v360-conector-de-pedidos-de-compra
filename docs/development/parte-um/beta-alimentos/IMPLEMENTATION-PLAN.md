A ingestão do **Beta Alimentos** não é “CSV simples com `;`”. O payload em `public/betaAlimentos/` é **dois arquivos relacionais**, em padrão BR, **com quebra de linha no meio do campo e sem aspas**. O OpenCSV só entra **depois** de remontar registros lógicos. Persistência, entidades e upsert **não se reescrevem**. **Nada disto foi implementado ainda.**

---

## O que isso é no projeto

O Alfa já fecha o pipeline: `POST /ingest/alfa` → DTO bruto → `AlfaIngestor` (`PedidoIngestor<T>`) → `PedidoService.upsert` → Postgres.

O Beta **repete esse desenho**, sem alterar o Alfa:

```
dois CSVs → remontagem (máquina de estados) → OpenCSV por posição
        → join por NUMERO_PEDIDO → BetaPedidoDTO
        → BetaIngestor → PedidoService.upsert (já existente)
```

Isso é o **DIA2**. OpenCSV **5.12.0 já está no `pom.xml`**.

---

## Particularidade: CSV sem aspas e `\n` no campo

Os arquivos oficiais **não** são RFC 4180. Não há aspas; o `\n` entra **no meio da célula**. OpenCSV cru trata isso como linha nova.

**`cabecalho.csv`** — `N = 6` colunas (`N-1 = 5` separadores `;` por registro):

| Campo | O que o arquivo tem |
|---|---|
| `SITUACAO` (`20260088412`) | `EM` + `\n` + `ABERTO` → **`EM ABERTO`** (quebra **não** está no último campo) |
| `FORNECEDOR_RAZAO_SOCIAL` (`20260088413`) | `Frigorífico Boa Mesa` + `\n` + `S.A.` → **`Frigorífico Boa Mesa S.A.`** |
| CNPJ | mascarado (`12.345.678/0001-90`) |
| `EMISSAO` | `dd/MM/yyyy` |

**`itens.csv`** — `N = 8` colunas (`N-1 = 7` separadores):

| Campo | Particularidade |
|---|---|
| Header | `PRECO_UNITARIO` partido: `PR` + `\n` + `ECO_UNITARIO` — quebra **no último campo**; a primeira linha física **já tem 7 `;`** |
| Dados | As 3 linhas de item vêm inteiras (sem `\n` interno) |
| Quantidades / preço | BR: `1.200,000`, `6,49` |
| Join | `NUMERO_PEDIDO` |

**Não usar** heurística de conteúdo (“linha não começa com `NUMERO_PEDIDO`”, “nome de coluna esperado”). Funciona só nos dois arquivos de exemplo; muda o ponto de quebra e a regra cai.

**Não usar** assimetria “header sem espaço / dado com espaço”. Isso só se resolve conhecendo o nome final da coluna. Com parse **por posição**, o texto do header é descartado; `\n` no meio do campo vira espaço de forma uniforme.

---

## Remontagem: máquina de estados + lookahead no último campo

Contrato estável: número de colunas fixo no spec (`6` cabeçalho, `8` itens). Percorrer o texto contando `;` desde o último registro fechado. Sem lista de nomes, sem “parece pedido”.

### Regras

Seja `seps` o número de `;` no registro atual.

1. **`\n` e `seps < N-1`** — o `\n` está **dentro** de um campo que não é o último. Trocar `\n` por espaço, **não** fechar o registro. Cobre `EM ABERTO` e `Frigorífico Boa Mesa S.A.`.

2. **`\n` e `seps == N-1`** — o cursor está no **último** campo. Só com o registro atual, `\n` de fim de linha e `\n` **dentro** do último campo são iguais. **Lookahead** da próxima linha **física**:

   | Lookahead | Ação |
   |---|---|
   | EOF | Fecha o registro. |
   | Próxima linha com **0** `;` | Continuação do último campo (caso `ECO_UNITARIO`). Concatena ( `\n` → espaço ) e segue. |
   | Próxima linha com **≥ 1** `;` | O `\n` é fim de registro; a próxima linha inicia outro registro. |

3. Emitir uma linha lógica `campo1;campo2;...;campoN` por registro fechado.

4. **Descartar o primeiro registro lógico** (header). Não importa se o último campo do header ficou `PR ECO_UNITARIO`: o mapeamento é por índice, não pelo nome.

### Por que o lookahead é obrigatório

A regra “já vi `N-1` `;` + `\n` → fecha” **quebra o `itens.csv` oficial**: fecha em `PR`, zera o contador e cola `ECO_UNITARIO` na primeira linha de **dados**.

O lookahead com “0 delimitadores na próxima linha física” continua sendo só contagem de `;`, não nome de coluna.

### Limite honesto (não é RFC 4180)

Se o **campo 1 do próximo registro** vier sozinho numa linha (0 `;`), lookahead e continuação do último campo se confundem. No spec do Beta o campo 1 é `NUMERO_PEDIDO` e o último campo de **dados** é `MOEDA` / `PRECO_UNITARIO` — quebra aí está fora do payload oficial. Cobrir em teste de boundary; não fingir parser com aspas.

---

## Camadas

| Peça | Papel | Não faz |
|---|---|---|
| Pré-processador | Máquina de estados + lookahead → linhas lógicas; descarta header | Persistência, join |
| OpenCSV | `;` + `@CsvBindByPosition` → DTOs de **linha** | Inferir colunas pelo nome |
| Join | Agrupar itens no pedido por `NUMERO_PEDIDO` | Normalizar CNPJ/status/data |
| DTOs `dto.beta` | Ordem das colunas de `public/` | Contrato JSON de saída |
| `BetaIngestor` | DTO → `Pedido` transiente, `clienteOrigem = "BETA"` | Banco |
| Normalizadores | CNPJ, status pt-BR, data BR, número BR, string | SQL |
| `PedidoService` | Upsert já feito | Não reimplementar |

**Reutilizar:** `CnpjSanitizer`, `StringSanitizer`, `StatusMapper`, `PedidoService`.

**Criar:** `BetaDateParser` (`dd/MM/yyyy` → `Instant` `America/Sao_Paulo`, mesmo critério do Alfa), `BetaStatusMapper`, `BetaNumberParser`. Sem `DateParser` genérico.

Status (DIA2): `EM ABERTO` → `OPEN`, `BLOQUEADO` → `BLOCKED`, `ENCERRADO` → `CLOSED` (o CSV de exemplo só tem os dois primeiros).

Unidade: persistir o que veio (`UN`, `KG`). Sem `fator_conv`.

Domínio esperado após o join (payload oficial):

- **20260088412** — Horizonte, CNPJ só dígitos, `OPEN`, BRL, 15/08/2026; itens `1` MAT-77 (1200 / 400 / 6,49) e `2` MAT-78 (500 / 0 / 4,15).
- **20260088413** — Frigorífico, `BLOCKED`, 01/08/2026; item `1` MAT-91 KG (2000 / 0 / 27,90).

---

## Plano de implementação

### 1. Contrato HTTP — `POST /ingest/beta`

`multipart/form-data` (`cabecalho` + `itens`). O controller orquestra; não parseia na mão.

### 2. Pré-processador (antes do OpenCSV)

Rotina parametrizada por `N` (`6` / `8`):

1. Ler UTF-8.
2. Aplicar a máquina de estados + lookahead.
3. Emitir registros lógicos; **pular o primeiro** (header).

Classe tipo remontador/parser de texto, **fora** do `BetaIngestor`.

### 3. DTOs de linha — OpenCSV por posição

**Não usar `@CsvBindByName`.** Beans com `@CsvBindByPosition`:

- Cabeçalho: `0` `NUMERO_PEDIDO`, `1` `FORNECEDOR_CNPJ`, `2` `FORNECEDOR_RAZAO_SOCIAL`, `3` `EMISSAO`, `4` `SITUACAO`, `5` `MOEDA`
- Item: `0` `NUMERO_PEDIDO`, `1` `ITEM`, `2` `CODIGO_MATERIAL`, `3` `DESCRICAO`, `4` `UNIDADE`, `5` `QTD_PEDIDA`, `6` `QTD_RECEBIDA`, `7` `PRECO_UNITARIO`

Números e data entram como **String**; conversão no parser/ingestor (`BigDecimal` / `LocalDate`).

Depois do join, `BetaPedidoDTO` (pedido + itens) alimenta `PedidoIngestor<BetaPedidoDTO>`.

### 4. Conversões BR

- `QTD_*` / `PRECO_UNITARIO`: remover `.` de milhar, `,` → `.` (`1.200,000` → `1200.000`, `6,49` → `6.49`).
- `EMISSAO` → `LocalDate` `dd/MM/yyyy` → `Instant` via `BetaDateParser`.

### 5. Join

`Map<NUMERO_PEDIDO, cabeçalho>` + itens na mesma chave.

- Item sem cabeçalho → erro explícito.
- Cabeçalho sem itens → rejeitar (equivalente ao `@NotEmpty` do Alfa).

### 6. `BetaIngestor` + `BetaController`

Igual ao Alfa: sanitizers/mappers; **não** setar `quantidade_pendente`; `dataIngestao` no service. Não mexer em `AlfaIngestor` / `AlfaController`.

### 7. Testes

Unitários no estilo Alfa (`new` / Mockito):

| Caso | Por quê |
|---|---|
| `public/betaAlimentos/` oficial | Contrato |
| Quebra em outro campo do meio (não `SITUACAO`) | Máquina não depende de conteúdo |
| Arquivo **sem** quebra de linha | Não “corrigir” o que já está certo |
| Header de itens partido no **último** campo | Lookahead; regressão `PR` / `ECO_UNITARIO` não vazar para dados |
| `1.200,000` e `6,49` | Número BR |
| `BetaStatusMapper` | Vocabulário pt-BR |
| Item sem cabeçalho / cabeçalho sem item | Join |
| `BetaIngestor` | `clienteOrigem = "BETA"`, CNPJ só dígitos |
| Suíte Alfa (~42) | Não regressão |

### 8. Fora deste recorte

- Hard delete de item órfão no upsert (§3.5.1): `PedidoService`, não CSV.
- Conferência, Gama, consulta: DIA3+.

---

## Ordem de implementação (quando autorizar)

1. Remontagem (máquina + lookahead) + testes de boundary, inclusive header partido no último campo.
2. OpenCSV por posição sobre as linhas lógicas.
3. Normalizadores Beta (número, data, status) + join → `BetaPedidoDTO`.
4. `BetaIngestor` + `POST /ingest/beta`.
5. Teste do ingestor (sem Spring; `PedidoService` mockado, como o Alfa).

O risco está no **passo 1**. Sem ele, OpenCSV lê o arquivo oficial errado.
