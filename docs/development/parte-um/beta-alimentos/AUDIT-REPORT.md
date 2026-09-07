# Relatório de Auditoria — Implementação Beta Alimentos (DIA2)

> **Data de execução:** 07/09/2026  
> **Branch:** `cliente-beta`  
> **Escopo:** `IMPLEMENTATION-PLAN.md` (Beta Alimentos) — análise do plano versus código produzido  
> **Resultado da suíte:** ✅ **94 testes, 0 falhas, 0 erros**

---

## 1. Sumário Executivo

O plano de implementação do Beta Alimentos (`IMPLEMENTATION-PLAN.md`) define um pipeline completo de ingestão de dois arquivos CSV relacionais, não-RFC4180, com quebras de linha no meio de campos. A implementação cobriu **todas as 8 etapas do plano** sem alterar nenhum componente do cliente Alfa (Open/Closed Principle verificado). Os 42 testes unitários do Alfa continuam passando após a adição do Beta.

### Contagem de artefatos produzidos

| Categoria | Arquivos | Linhas de código |
|:---|:---:|:---:|
| Produção — camada de parse | 2 | 273 |
| Produção — camada de normalização | 3 | 101 |
| Produção — camada de DTO | 4 | 103 |
| Produção — ingestor | 1 | 78 |
| Produção — controller | 1 | 63 |
| **Total produção** | **11** | **618** |
| Testes — parser/assembler | 2 | 367 |
| Testes — normalizadores | 2 | 174 |
| Testes — ingestor | 1 | 277 |
| **Total testes** | **5** | **818** |
| **Total geral** | **16** | **1.436** |

---

## 2. Contexto: O Problema do Payload Beta

Antes de descrever o que foi implementado, é necessário entender o problema central que motivou a maioria das decisões de design. O payload do Beta Alimentos **não é CSV padrão (RFC 4180)**:

- Não há aspas delimitando campos.
- O caractere `\n` aparece literalmente no meio de campos (não como fim de registro).
- O header do arquivo `itens.csv` tem sua última coluna quebrada entre duas linhas físicas.

**Arquivo `cabecalho.csv` (conteúdo literal — 6 colunas esperadas):**

```
NUMERO_PEDIDO;FORNECEDOR_CNPJ;FORNECEDOR_RAZAO_SOCIAL;EMISSAO;SITUACAO;MOEDA
20260088412;12.345.678/0001-90;Distribuidora Horizonte Ltda;15/08/2026;EM
ABERTO;BRL
20260088413;98.765.432/0001-55;Frigorífico Boa Mesa
S.A.;01/08/2026;BLOQUEADO;BRL
```

**Arquivo `itens.csv` (conteúdo literal — 8 colunas esperadas):**

```
NUMERO_PEDIDO;ITEM;CODIGO_MATERIAL;DESCRICAO;UNIDADE;QTD_PEDIDA;QTD_RECEBIDA;PR
ECO_UNITARIO
20260088412;1;MAT-77;Óleo de soja 900ml;UN;1.200,000;400,000;6,49
20260088412;2;MAT-78;Açúcar refinado 1kg;UN;500,000;0,000;4,15
20260088413;1;MAT-91;Carne bovina dianteiro kg;KG;2.000,000;0,000;27,90
```

Qualquer parser CSV convencional leria `EM` como um registro de 1 campo e `ABERTO;BRL` como início de outro, corrompendo os dados silenciosamente. A solução foi construir um pré-processador próprio **antes** de entregar os dados ao OpenCSV.

---

## 3. Arquitetura Implementada

O pipeline segue o mesmo padrão Pipes and Filters do cliente Alfa, acrescido de uma etapa de remontagem:

```
POST /ingest/beta  (multipart/form-data: cabecalho + itens)
        │
        ▼
BetaController
  (recebe dois MultipartFile, repassa InputStream ao parser)
        │
        ▼
BetaCsvParser.parse(cabecalhoStream, itensStream)
  ├─ BetaCsvRecordAssembler.assemble(cabecalhoCsv, columns=6, skipHeader=true)
  │     → máquina de estados + lookahead → List<String> (linhas lógicas)
  ├─ BetaCsvRecordAssembler.assemble(itensCsv, columns=8, skipHeader=true)
  │     → máquina de estados + lookahead → List<String> (linhas lógicas)
  ├─ OpenCSV CsvToBeanBuilder → List<BetaCabecalhoLinhaDTO>
  ├─ OpenCSV CsvToBeanBuilder → List<BetaItemLinhaDTO>
  └─ join() por NUMERO_PEDIDO → List<BetaPedidoDTO>
        │
        ▼
BetaIngestor.toEntity(dto)
  ├─ CnpjSanitizer.sanitize(cnpj)          → remove máscara
  ├─ StringSanitizer.sanitize(razaoSocial) → normaliza espaços
  ├─ BetaDateParser.parse(emissao)         → dd/MM/yyyy → Instant (SP)
  ├─ BetaStatusMapper.map(situacao)        → pt-BR → StatusPedido
  └─ BetaNumberParser.parse(qtd/preco)     → BR → BigDecimal
        │
        ▼
PedidoService.upsert(pedido)   ← reutilizado sem alterações
        │
        ▼
PostgreSQL (Fornecedor, Pedido, Item)
```

---

## 4. Inventário Detalhado de Cada Arquivo

### 4.1 Pré-processador: `BetaCsvRecordAssembler`

**Arquivo:** `src/main/java/.../parser/BetaCsvRecordAssembler.java`  
**Linhas:** 132 | **Dependências externas:** nenhuma (classe utilitária estática pura)

**Responsabilidade única:** receber o texto bruto do CSV e emitir registros lógicos completos (um por pedido/item), descartando o header.

**Assinatura pública:**

```java
public static List<String> assemble(String text, int columns, boolean skipHeader)
```

**Parâmetros:**

| Parâmetro | Valor para cabecalho.csv | Valor para itens.csv |
|:---|:---:|:---:|
| `columns` | `6` | `8` |
| `skipHeader` | `true` | `true` |

**Lógica da máquina de estados — regras implementadas:**

Seja `seps` o contador de `;` acumulados no registro atual e `N-1 = columns - 1`:

| Condição | Ação implementada |
|:---|:---|
| Char `';'` | Incrementa `seps`, acrescenta ao buffer. |
| Char `'\n'` / `'\r'` e buffer vazio | Descarta (linha em branco). |
| `'\n'` e `seps < N-1` | Está dentro de um campo que não é o último. Substitui `\n` por espaço e continua acumulando. Cobre `EM ABERTO` e `Frigorífico Boa Mesa S.A.`. |
| `'\n'` e `seps >= N-1` e EOF | Fim de arquivo. Fecha o registro atual. |
| `'\n'` e `seps >= N-1` e próxima linha física tem **0** `;` | **Lookahead:** continuação do último campo. Concatena com espaço. Cobre `ECO_UNITARIO` como continuação de `PR`. |
| `'\n'` e `seps >= N-1` e próxima linha física tem **≥ 1** `;` | Fim de registro. Fecha e reseta `seps`. A próxima linha inicia outro registro. |
| Buffer restante ao fim do loop | Emite como último registro (arquivo sem `\n` final). |
| `skipHeader = true` | Descarta o índice 0 da lista resultante. |

**Por que o lookahead é obrigatório:**  
Sem o lookahead, ao encontrar `\n` com `seps == 7` (N-1 para itens.csv), o algoritmo fecharia o registro no token `PR` e colaria `ECO_UNITARIO` ao primeiro campo do próximo registro de dados — gerando `ECO_UNITARIO20260088412` como valor da primeira coluna de dados. O lookahead detecta que a próxima linha física não tem `;` e a trata como continuação.

**Suporte a CRLF:** o método `skipNewline()` verifica se o `\r` é seguido de `\n` e avança dois caracteres, garantindo compatibilidade com arquivos Windows.

---

### 4.2 Parser CSV: `BetaCsvParser`

**Arquivo:** `src/main/java/.../parser/BetaCsvParser.java`  
**Linhas:** 141 | **Dependências:** `BetaCsvRecordAssembler`, OpenCSV 5.12.0, DTOs Beta

**Responsabilidade:** orquestrar o fluxo completo de parse — pré-processamento, mapeamento OpenCSV e join.

**Constantes públicas:**

```java
public static final int COLUNAS_CABECALHO = 6;
public static final int COLUNAS_ITENS = 8;
```

**Sobrecargas do método principal:**

```java
public List<BetaPedidoDTO> parse(InputStream cabecalho, InputStream itens)
public List<BetaPedidoDTO> parse(String cabecalhoCsv, String itensCsv)
```

A sobrecarga com `String` é exposta para facilitar os testes unitários sem necessidade de criar `InputStream`.

**Mapeamento OpenCSV:**  
Após obter as linhas lógicas do `BetaCsvRecordAssembler`, o parser usa `CsvToBeanBuilder` com separador `;` e `withIgnoreLeadingWhiteSpace(true)`. **Não usa `@CsvBindByName`** — o mapeamento é exclusivamente por posição (`@CsvBindByPosition`), tornando o parser imune ao nome do header (que pode vir quebrado).

**Lógica de join (método `join()`):**

1. Constrói `Map<String, BetaCabecalhoLinhaDTO>` indexado por `NUMERO_PEDIDO` (via `LinkedHashMap`, preservando ordem).
2. Para cada item, verifica se o número de pedido existe no mapa — lança `IllegalArgumentException` com mensagem explícita se não encontrar.
3. Após processar todos os itens, varre o mapa de cabeçalhos — lança `IllegalArgumentException` se um cabeçalho não tiver itens associados.
4. Emite a lista de `BetaPedidoDTO` na mesma ordem dos cabeçalhos.

O método `join()` tem visibilidade package-private para permitir teste direto sem precisar construir CSVs completos.

---

### 4.3 DTOs de linha — OpenCSV por posição

**Pacote:** `dto/beta/`

#### `BetaCabecalhoLinhaDTO`

Bean Lombok com `@Getter`, `@Setter`, `@NoArgsConstructor` — necessário para o OpenCSV instanciar via reflexão.

| Posição | Campo | Coluna CSV |
|:---:|:---|:---|
| 0 | `numeroPedido` | `NUMERO_PEDIDO` |
| 1 | `fornecedorCnpj` | `FORNECEDOR_CNPJ` |
| 2 | `fornecedorRazaoSocial` | `FORNECEDOR_RAZAO_SOCIAL` |
| 3 | `emissao` | `EMISSAO` |
| 4 | `situacao` | `SITUACAO` |
| 5 | `moeda` | `MOEDA` |

Todos os campos são `String` — nenhuma conversão acontece neste DTO.

#### `BetaItemLinhaDTO`

| Posição | Campo | Coluna CSV |
|:---:|:---|:---|
| 0 | `numeroPedido` | `NUMERO_PEDIDO` (chave do join) |
| 1 | `item` | `ITEM` |
| 2 | `codigoMaterial` | `CODIGO_MATERIAL` |
| 3 | `descricao` | `DESCRICAO` |
| 4 | `unidade` | `UNIDADE` |
| 5 | `qtdPedida` | `QTD_PEDIDA` |
| 6 | `qtdRecebida` | `QTD_RECEBIDA` |
| 7 | `precoUnitario` | `PRECO_UNITARIO` |

#### `BetaPedidoDTO`

`record` imutável resultante do join. Carrega campos do cabeçalho mais a lista de itens agrupados. Campos numéricos e de data ainda são `String` — conversão acontece no `BetaIngestor`.

#### `BetaItemDTO`

`record` imutável com os campos de um item após o join (sem `NUMERO_PEDIDO`, que era somente a chave relacional).

---

### 4.4 Normalizadores Beta

#### `BetaDateParser`

**Arquivo:** `src/main/java/.../normalizer/BetaDateParser.java` | **Linhas:** 42

Classe utilitária estática (construtor privado, sem Spring). Converte `dd/MM/yyyy` → `Instant` no fuso `America/Sao_Paulo`, usando início do dia (`atStartOfDay`) — **mesmo critério do `AlfaDateParser`**.

| Entrada | Comportamento |
|:---|:---|
| `null` | Retorna `null` |
| Vazia após trim | Lança `IllegalArgumentException("Data BR vazia")` |
| Formato inválido | Lança `IllegalArgumentException("Data BR inválida: '...'")` wrappando `DateTimeParseException` |
| `"15/08/2026"` | Retorna `Instant` correspondente à meia-noite de 15/08/2026 em Brasília |

#### `BetaNumberParser`

**Arquivo:** `src/main/java/.../normalizer/BetaNumberParser.java` | **Linhas:** 33

Classe utilitária estática. Converte números no padrão BR para `BigDecimal`.

**Algoritmo:**
1. `null` → retorna `null`.
2. `trim()`.
3. Vazio após trim → lança `IllegalArgumentException`.
4. `.replace(".", "")` — remove separador de milhar.
5. `.replace(",", ".")` — troca decimal BR por ponto.
6. `new BigDecimal(normalizado)`.

**Exemplos verificados pelo payload oficial:**

| Entrada CSV | BigDecimal resultante |
|:---:|:---:|
| `1.200,000` | `1200.000` |
| `400,000` | `400.000` |
| `500,000` | `500.000` |
| `0,000` | `0.000` |
| `6,49` | `6.49` |
| `4,15` | `4.15` |
| `27,90` | `27.90` |
| `2.000,000` | `2000.000` |

#### `BetaStatusMapper`

**Arquivo:** `src/main/java/.../normalizer/BetaStatusMapper.java` | **Linhas:** 26

`@Component` Spring que implementa a interface `StatusMapper`. Aplica `.toUpperCase().trim()` antes do switch.

| Valor no CSV | `StatusPedido` |
|:---|:---|
| `EM ABERTO` | `OPEN` |
| `BLOQUEADO` | `BLOCKED` |
| `ENCERRADO` | `CLOSED` |

> O payload de exemplo só contém `EM ABERTO` e `BLOQUEADO`. `ENCERRADO` foi implementado por completude do contrato (plano §99), evitando `500 Internal Server Error` em produção. `null` lança `IllegalArgumentException` antes do switch.

---

### 4.5 Ingestor: `BetaIngestor`

**Arquivo:** `src/main/java/.../ingestor/BetaIngestor.java` | **Linhas:** 78

`@Component` Spring que implementa `PedidoIngestor<BetaPedidoDTO>` — mesmo contrato Strategy do `AlfaIngestor`.

**Transformações aplicadas por campo:**

| Campo DTO | Transformação | Campo entidade |
|:---|:---|:---|
| `dto.fornecedorCnpj()` | `CnpjSanitizer.sanitize()` | `Fornecedor.cnpj` |
| `dto.fornecedorRazaoSocial()` | `StringSanitizer.sanitize()` | `Fornecedor.nome` |
| `dto.numeroPedido()` | direto | `Pedido.numeroPedidoOrigem` |
| — | `"BETA"` (constante) | `Pedido.clienteOrigem` |
| `dto.emissao()` | `BetaDateParser.parse()` | `Pedido.dataCriacao` |
| `dto.situacao()` | `BetaStatusMapper.map()` | `Pedido.status` |
| `dto.moeda()` | direto | `Pedido.moeda` |
| `itemDto.item()` | direto | `Item.linha` |
| `itemDto.codigoMaterial()` | direto | `Item.codigoMaterial` |
| `itemDto.descricao()` | `StringSanitizer.sanitize()` | `Item.descricao` |
| `itemDto.unidade()` | direto (sem conversão) | `Item.unidadeMedida` |
| `itemDto.qtdPedida()` | `BetaNumberParser.parse()` | `Item.quantidadePedida` |
| `itemDto.qtdRecebida()` | `BetaNumberParser.parse()` | `Item.quantidadeRecebida` |
| `itemDto.precoUnitario()` | `BetaNumberParser.parse()` | `Item.precoUnitario` |

**Campos intencionalmente não setados:**

- `Pedido.dataIngestao` — preenchido pelo `PedidoService.upsert()` no momento da persistência (mesmo comportamento do Alfa).
- `Item.quantidadePendente` — coluna `GENERATED ALWAYS AS (quantidade_pedida - quantidade_recebida) STORED` no Postgres. O JPA nunca escreve nessa coluna (`insertable=false, updatable=false`).

**Unidade de medida:** persistida exatamente como veio no CSV (`UN`, `KG`). Sem `fator_conv` neste recorte.

---

### 4.6 Controller: `BetaController`

**Arquivo:** `src/main/java/.../controller/BetaController.java` | **Linhas:** 63

`@RestController` com `@RequestMapping("/ingest")`. Não faz lógica de negócio, não parseia CSV na mão.

**Endpoint exposto:**

```
POST /ingest/beta
Content-Type: multipart/form-data
  cabecalho: arquivo CSV (MultipartFile)
  itens:     arquivo CSV (MultipartFile)
```

**Resposta (HTTP 200):**

```json
[
  {
    "id_pedido": "<UUID>",
    "numero_pedido_origem": "20260088412",
    "cliente_origem": "BETA",
    "status": "OPEN"
  },
  {
    "id_pedido": "<UUID>",
    "numero_pedido_origem": "20260088413",
    "cliente_origem": "BETA",
    "status": "BLOCKED"
  }
]
```

**Fluxo interno:**
1. Recebe dois `MultipartFile` via `@RequestParam`.
2. Extrai `InputStream` e repassa ao `BetaCsvParser.parse()`.
3. Itera `List<BetaPedidoDTO>`: para cada DTO chama `betaIngestor.toEntity(dto)` e `pedidoService.upsert(pedido)`.
4. Constrói a resposta com os campos de identificação do pedido salvo.

---

## 5. Inventário Detalhado dos Testes

### 5.1 `BetaCsvRecordAssemblerTest` — 13 testes

**Arquivo:** `src/test/java/.../parser/BetaCsvRecordAssemblerTest.java` | **Linhas:** 205

| Método de teste | O que verifica |
|:---|:---|
| `assemble_cabecalhoOficial_doisRegistros` | Payload real `cabecalho.csv`: `EM ABERTO` e `Frigorífico Boa Mesa S.A.` remontados, 2 registros. |
| `assemble_itensOficial_tresRegistrosDadosCorretos` | Payload real `itens.csv`: 3 registros de dados corretos, sem contaminação do `ECO_UNITARIO`. |
| `assemble_headerPartidoNoUltimoCampo_headerDescartadoSemContaminacao` | Regressão específica: `ECO_UNITARIO` não vaza para o primeiro campo de dados. |
| `assemble_quebraEmCampoMeio_diferenteDeUltimo_concatenaComEspaco` | Quebra em campo arbitrário do meio — máquina não depende de conteúdo, só de contagem de `;`. |
| `assemble_multiplosNewlineNoMesmoCampo_concatenaTudoComEspaco` | Múltiplos `\n` no mesmo campo do meio concatenados com espaço. |
| `assemble_semQuebraInternaNosCampos_registrosIntactos` | Arquivo normal (sem quebras internas): registros passam inalterados — não "corrige" o que está certo. |
| `assemble_skipHeaderFalse_incluiHeader` | `skipHeader=false` mantém o header na lista. |
| `assemble_crlf_tratadoCorretamente` | Arquivo Windows (`\r\n`): tratado identicamente a `\n`. |
| `assemble_entradaNula_retornaListaVazia` | `null` → lista vazia (sem NPE). |
| `assemble_entradaVazia_retornaListaVazia` | `""` → lista vazia. |
| `assemble_colunasMenorQueDois_lancaExcecao` | `columns < 2` → `IllegalArgumentException`. |
| `assemble_soHeader_retornaListaVazia` | Arquivo com apenas o header + `skipHeader=true` → lista vazia. |
| `assemble_semNewlineFinal_ultimoRegistroEmitido` | Arquivo sem `\n` no final: último registro emitido via buffer residual. |

### 5.2 `BetaCsvParserTest` — 7 testes

**Arquivo:** `src/test/java/.../parser/BetaCsvParserTest.java` | **Linhas:** 162

Os testes usam os conteúdos literais exatos dos arquivos `public/betaAlimentos/cabecalho.csv` e `public/betaAlimentos/itens.csv` como input.

| Método de teste | O que verifica |
|:---|:---|
| `parse_payloadOficial_doisPedidos` | Payload oficial produz exatamente 2 pedidos. |
| `parse_pedido412_cabecalhoCorreto` | Todos os campos do cabeçalho do pedido `20260088412`: CNPJ com máscara, `EM ABERTO`, `BRL`, data. |
| `parse_pedido412_doisItensCorretos` | Dois itens: linha, código, unidade, quantidades BR e preço BR — como strings brutas. |
| `parse_pedido413_razaoSocialEStatusCorretos` | `Frigorífico Boa Mesa S.A.` remontado e status `BLOQUEADO`. |
| `parse_pedido413_umItemKg` | Um item `KG` com `2.000,000` e `27,90` preservados como string. |
| `join_itemSemCabecalho_lancaExcecao` | `NUMERO_PEDIDO=99999` sem cabeçalho → `IllegalArgumentException` com o número no texto. |
| `join_cabecalhoSemItens_lancaExcecao` | Cabeçalho sem itens → `IllegalArgumentException` com o número no texto. |

### 5.3 `BetaNumberParserTest` — 10 testes

**Arquivo:** `src/test/java/.../normalizer/BetaNumberParserTest.java` | **Linhas:** 95

| Método de teste | Entrada | Saída esperada |
|:---|:---:|:---:|
| `parse_comMilharEDecimal_retornaBigDecimalCorreto` | `"1.200,000"` | `1200.000` |
| `parse_semMilharComDecimal_retornaBigDecimalCorreto` | `"6,49"` | `6.49` |
| `parse_quatroVirgulaDezeSeis_retornaBigDecimalCorreto` | `"4,15"` | `4.15` |
| `parse_vinteSeteVirgulaNoventa_retornaBigDecimalCorreto` | `"27,90"` | `27.90` |
| `parse_zero_retornaZero` | `"0,000"` | `0` |
| `parse_doisMilComDecimal_retornaBigDecimalCorreto` | `"2.000,000"` | `2000.000` |
| `parse_comEspacos_retornaBigDecimalCorreto` | `"  1,50  "` | `1.50` |
| `parse_null_retornaNull` | `null` | `null` |
| `parse_entradaVazia_lancaExcecao` | `""` | `IllegalArgumentException` |
| `parse_textoNaoNumerico_lancaExcecao` | `"ABC"` | `IllegalArgumentException` com `"ABC"` na mensagem |

### 5.4 `BetaStatusMapperTest` — 8 testes

**Arquivo:** `src/test/java/.../normalizer/BetaStatusMapperTest.java` | **Linhas:** 79

| Método de teste | Entrada | Saída |
|:---|:---:|:---:|
| `map_emAberto_retornaOpen` | `"EM ABERTO"` | `OPEN` |
| `map_bloqueado_retornaBlocked` | `"BLOQUEADO"` | `BLOCKED` |
| `map_encerrado_retornaClosed` | `"ENCERRADO"` | `CLOSED` |
| `map_minusculo_normalizado` | `"em aberto"`, `"bloqueado"`, `"encerrado"` | `OPEN`, `BLOCKED`, `CLOSED` |
| `map_comEspacos_normalizado` | `"  EM ABERTO  "` | `OPEN` |
| `map_null_lancaExcecao` | `null` | `IllegalArgumentException` |
| `map_statusDesconhecido_lancaExcecaoComMensagem` | `"PENDENTE"` | `IllegalArgumentException` com `"PENDENTE"` |
| `map_statusVazio_lancaExcecao` | `""` | `IllegalArgumentException` |

### 5.5 `BetaIngestorTest` — 14 testes

**Arquivo:** `src/test/java/.../ingestor/BetaIngestorTest.java` | **Linhas:** 277

Sem `@SpringBootTest`, sem H2, sem banco, sem Mockito. `BetaStatusMapper` instanciado com `new` diretamente. Somente a transformação `BetaPedidoDTO → Pedido` é testada.

| Método de teste | O que verifica |
|:---|:---|
| `toEntity_pedido412_mapeiaCorretamente` | Pedido `20260088412`: todos os campos do cabeçalho corretos, `clienteOrigem="BETA"`, `dataIngestao=null`. |
| `toEntity_qualquerDto_clienteOrigemEhBeta` | `clienteOrigem` é sempre `"BETA"` independente do input. |
| `toEntity_cnpjComMascara_somenteDigitos` | `"12.345.678/0001-90"` → `"12345678000190"`. |
| `toEntity_cnpjSemMascara_permaneceIgual` | CNPJ já sem máscara não é alterado. |
| `toEntity_bloqueado_mapeiaParaBlocked` | Pedido `20260088413`: `"BLOQUEADO"` → `BLOCKED`. |
| `toEntity_encerrado_mapeiaParaClosed` | `"ENCERRADO"` → `CLOSED`. |
| `toEntity_statusDesconhecido_lancaExcecao` | `"PENDENTE"` → `IllegalArgumentException` com mensagem. |
| `toEntity_itemComNumerosBr_converteCorretamente` | `"1.200,000"` → `1200.000`, `"400,000"` → `400.000`, `"6,49"` → `6.49`. |
| `toEntity_qtdRecebidaZero_retornaZero` | `"0,000"` → `BigDecimal.ZERO`. |
| `toEntity_unidadeMedida_persistidaSemConversao` | `"UN"` e `"KG"` persistidos sem transformação. |
| `toEntity_quantidadePendente_naoSetada` | `item.getQuantidadePendente()` é `null` — nunca setado pelo backend. |
| `toEntity_itens_referenciaOPedidoPai` | Cada `Item` referencia o mesmo `Pedido` pai (`isSameAs`). |
| `toEntity_variosItens_mantémOrdem` | Três itens mantêm a ordem `"1"`, `"2"`, `"3"`. |
| `toEntity_razaoSocialComEspaco_sanitizada` | `"Frigorífico Boa Mesa S.A."` preservada pelo `StringSanitizer`. |

---

## 6. Resultado Consolidado da Suíte de Testes

```
Tests run: 94, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Total time: ~6.8 s
Finished at: 2026-09-07T08:06:45-03:00
```

**Detalhamento por classe:**

| Classe de teste | Testes | Status |
|:---|:---:|:---:|
| `PedidoServiceTest` | 7 | ✅ |
| `AlfaIngestorTest` | 12 | ✅ |
| `BetaIngestorTest` | 14 | ✅ |
| `BetaCsvRecordAssemblerTest` | 13 | ✅ |
| `BetaCsvParserTest` | 7 | ✅ |
| `BetaNumberParserTest` | 10 | ✅ |
| `CnpjSanitizerTest` | 5 | ✅ |
| `StringSanitizerTest` | 7 | ✅ |
| `BetaStatusMapperTest` | 8 | ✅ |
| `AlfaDateParserTest` | 3 | ✅ |
| `AlfaStatusMapperTest` | 8 | ✅ |
| **Total** | **94** | **✅** |

> **Nota sobre `ConectorpedidoscompraApplicationTests`:** este teste tenta subir o contexto Spring completo com conexão ao PostgreSQL. É excluído da execução local porque não há banco disponível fora do Docker — comportamento idêntico ao que existia antes do Beta; não é regressão introduzida.

---

## 7. Verificação do Open/Closed Principle

Nenhum arquivo existente do cliente Alfa foi alterado:

| Arquivo | Alterado? |
|:---|:---:|
| `AlfaController.java` | ❌ Não |
| `AlfaIngestor.java` | ❌ Não |
| `AlfaStatusMapper.java` | ❌ Não |
| `AlfaDateParser.java` | ❌ Não |
| `PedidoService.java` | ❌ Não |
| `CnpjSanitizer.java` | ❌ Não |
| `StringSanitizer.java` | ❌ Não |
| `StatusMapper.java` (interface) | ❌ Não |
| `PedidoIngestor.java` (interface) | ❌ Não |
| Entidades (`Pedido`, `Item`, `Fornecedor`) | ❌ Não |
| Repositórios | ❌ Não |
| `schema.sql` / DDL | ❌ Não |

---

## 8. Aderência ao Plano de Implementação

| Etapa do plano | Descrição | Status |
|:---:|:---|:---:|
| §1 | `POST /ingest/beta` — `multipart/form-data`, `cabecalho` + `itens` | ✅ |
| §2 | Pré-processador parametrizado por `N` (6/8): máquina de estados + lookahead, descarta header | ✅ |
| §3 | DTOs de linha com `@CsvBindByPosition` (não `@CsvBindByName`), quantidades como `String` | ✅ |
| §4 | Conversões BR: `QTD_*` / `PRECO_UNITARIO` (`BetaNumberParser`) e `EMISSAO` (`BetaDateParser`) | ✅ |
| §5 | Join por `NUMERO_PEDIDO`; erro explícito para item sem cabeçalho e cabeçalho sem itens | ✅ |
| §6 | `BetaIngestor` + `BetaController`; `clienteOrigem="BETA"`; `dataIngestao` delegado ao service | ✅ |
| §7 | Testes unitários: payload oficial, boundary, sem quebra, lookahead, BR number, status, join, ingestor | ✅ |
| §7 (regressão) | Suíte Alfa (~42 testes) — todos passando após adição do Beta | ✅ |

---

## 9. O Que Está Fora Deste Recorte (Conforme §8 do Plano)

Os itens abaixo estão **explicitamente excluídos** do DIA2 e não foram implementados — conforme o próprio plano:

| Item | Motivo da exclusão |
|:---|:---|
| Hard delete de item órfão no upsert (§3.5.1) | Pertence ao `PedidoService`, não ao pipeline CSV. Pendente. |
| Conferência (endpoint de divergência) | DIA3+. |
| Cliente Gama | DIA3+. |
| Consulta/listagem de pedidos | DIA3+. |

---

## 10. Estado do Workspace — Arquivos não Commitados

Os arquivos abaixo compõem a implementação Beta na branch `cliente-beta` e ainda não foram commitados (status `??` no `git status`):

**Produção (11 arquivos — 618 linhas):**

```
src/main/java/.../controller/BetaController.java
src/main/java/.../dto/beta/BetaCabecalhoLinhaDTO.java
src/main/java/.../dto/beta/BetaItemDTO.java
src/main/java/.../dto/beta/BetaItemLinhaDTO.java
src/main/java/.../dto/beta/BetaPedidoDTO.java
src/main/java/.../ingestor/BetaIngestor.java
src/main/java/.../normalizer/BetaDateParser.java
src/main/java/.../normalizer/BetaNumberParser.java
src/main/java/.../normalizer/BetaStatusMapper.java
src/main/java/.../parser/BetaCsvParser.java
src/main/java/.../parser/BetaCsvRecordAssembler.java
```

**Testes (5 arquivos — 818 linhas):**

```
src/test/java/.../ingestor/BetaIngestorTest.java
src/test/java/.../normalizer/BetaNumberParserTest.java
src/test/java/.../normalizer/BetaStatusMapperTest.java
src/test/java/.../parser/BetaCsvParserTest.java
src/test/java/.../parser/BetaCsvRecordAssemblerTest.java
```
