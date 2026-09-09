# Fluxo lógico e análise da implementação atual

> **Data:** 08/09/2026  
> **Escopo:** reconstrução do caminho de desenvolvimento a partir de `docs/` (âncora operacional: `docs/desing/3 ETAPA DESING.md`) e auditoria do código em `src/` no estado atual.  
> **Propósito deste arquivo:** descrever *como* o projeto chegou até aqui, *o que* está de fato no código, *quais estratégias* sustentam o comportamento de hoje, e *onde* documentação, arquitetura e implementação ainda divergem.

O documento operacional `3 ETAPA DESING.md` (cópia de trabalho do `DESING.md`, alinhada à análise estrutural de 06/09/2026) ainda marca Beta, consulta, conferência, relatório e Gama como **a fazer**. O código **já ultrapassou** esse recorte: Parte 1 (Alfa + Beta + consulta + conferência + relatório) está implementada. Gama (Parte 2) e a tag `parte-1` **não**.

---

## Índice

1. [Propósito da aplicação](#1-propósito-da-aplicação)
2. [Como a documentação foi usada (fluxo de trabalho)](#2-como-a-documentação-foi-usada-fluxo-de-trabalho)
3. [Fluxo lógico de desenvolvimento até hoje](#3-fluxo-lógico-de-desenvolvimento-até-hoje)
4. [Arquitetura alvo vs. arquitetura real](#4-arquitetura-alvo-vs-arquitetura-real)
5. [Fluxo de execução atual (ponta a ponta)](#5-fluxo-de-execução-atual-ponta-a-ponta)
6. [Implementações concretizadas](#6-implementações-concretizadas)
7. [Estratégias tomadas para o sistema funcionar](#7-estratégias-tomadas-para-o-sistema-funcionar)
8. [Validação: o que está sólido](#8-validação-o-que-está-sólido)
9. [Estratégias mal elaboradas ou frágeis na prática](#9-estratégias-mal-elaboradas-ou-frágeis-na-prática)
10. [Dados e contratos inconsistentes](#10-dados-e-contratos-inconsistentes)
11. [Documentação desatualizada (dívida documental)](#11-documentação-desatualizada-dívida-documental)
12. [O que ainda falta (honesto)](#12-o-que-ainda-falta-honesto)
13. [Conclusão](#13-conclusão)

---

## 1. Propósito da aplicação

O **Conector de Pedidos de Compra** é um backend REST (Java 21 / Spring Boot) que atua como camada intermediária da V360:

1. **Ingere** pedidos de compra de ERPs distintos, cada um no formato bruto oficial em `public/` (não no contrato unificado).
2. **Normaliza** CNPJ, status, datas, strings e números para um **modelo único** (`Fornecedor` + `Pedido` + `Item`) persistido em PostgreSQL.
3. **Consulta** pedidos com filtros.
4. **Confere** uma nota fiscal contra o pedido persistido, acumulando divergências e gravando histórico (`Conferencia` + `Divergencia`).
5. **Agrega** esse histórico em relatório (`APROVADA` / `REJEITADA` e contagem por tipo).

Clientes previstos no desafio:

| Cliente | Formato oficial | Estado no código |
|---|---|---|
| Alfa Energia | JSON aninhado (`purchase_orders`) | Implementado |
| Beta Alimentos | Dois CSVs `;` padrão BR, não RFC 4180 | Implementado |
| Gama Logística | JSON achatado (1 objeto = 1 linha) | **Não implementado** |

A conferência **não dá baixa** em `quantidade_recebida`. O saldo pendente vem da ingestão (coluna gerada `pedida − recebida`). A nota fiscal é um cruzamento de conferência, não um evento de recebimento físico.

---

## 2. Como a documentação foi usada (fluxo de trabalho)

O repositório não foi desenvolvido “código primeiro”. Houve um ciclo deliberado de **design → plano → implementação → auditoria → correção**, visível em `docs/`:

```
Enunciado / payloads em public/
        │
        ▼
DESING.md (decisões + kanban misturados)
        │  (AI_USAGE.md: tokens demais; cronograma separado)
        ▼
docs/tasks/DIA1.md … DIA7.md     +     docs/desing/DESING.md
        │
        ▼
docs/ANALISE_ESTRUTURAL_PROJETO.md (06/09 — só Alfa no código)
        │
        ▼
docs/desing/3 ETAPA DESING.md     ← âncora operacional
        │  (decisões 1–4 fechadas; Beta/Gama/conferência ainda “a fazer”)
        ▼
Planos por fatia
  • entities / Alfa / testes  (first-version → second-version)
  • beta-alimentos/IMPLEMENTATION-PLAN.md + AUDIT-REPORT.md
  • divergencia-conferencia/IMPLEMENTATION-PLAN.md
  • CLAUDE_HELP.md (consulta)
  • PLAN-CORRECTION-CURSOR.md (diagnóstico: IA anterior inventou regras)
        │
        ▼
Código atual (Parte 1 no src/; Gama ausente)
```

**Estratégia de contexto para IA** (`docs/desing/AI_USAGE.md`):

- Gemini: pesquisa e rascunho de arquitetura; revisão de output.
- Claude: crítica do desenho e implementação.
- Separar cronograma (`DIA*.md`) do design para não inflar o prompt.
- Cada fatia: plano escrito → código → análise de bug (`analise_bug_payload_alfa.md`, `analise_inconsistencias_alfa.md`) → segundo ciclo.
- Payloads reais em `public/` como fonte da verdade de **entrada**; o JSON da seção 2 do design é contrato de **domínio/saída**.

Isso explica dois hábitos que ainda aparecem no código: DTOs de entrada espelham o bruto do cliente (`po_number`, `vendor`, CSV por posição) e o upsert/consulta/conferência falam o vocabulário unificado (`numero_pedido`, `cliente_origem`, `OPEN`).

---

## 3. Fluxo lógico de desenvolvimento até hoje

Ordem real (commits + docs), não a ordem ainda marcada nos checklists `DIA*.md`.

### Etapa 0 — Decisões de plataforma

- Postgres desde o dia zero (`compose.yaml`: Postgres 17 + app + pgAdmin), não H2 como banco da aplicação.
- Schema em `schema.sql` + `ddl-auto=validate` + `sql.init.mode=always` (Hibernate não redesenha tabelas).
- Upsert via JPA (`find` + `save`), não `INSERT … ON CONFLICT`.
- Uma rota HTTP por cliente; **Factory formal dispensada**; **Strategy** na interface `PedidoIngestor<T>`.
- Camadas: Controller (validação/rota) → Ingestor (DTO → entidade) → Normalizadores → Service → Repository → Postgres.

### Etapa 1 — DIA1: Alfa (setup + ingestão)

Concretizado e coberto por testes unitários:

- Entidades `Fornecedor`, `Pedido`, `Item`; repositórios; `StatusPedido`.
- `CnpjSanitizer`, `StatusMapper` + `AlfaStatusMapper`, `AlfaDateParser`, depois `StringSanitizer`.
- DTOs `dto.alfa` alinhados a `public/alfaEnergiaPayload.json` (lote em `purchase_orders`).
- `AlfaIngestor` + `POST /ingest/alfa` + `PedidoService.upsert`.

**Correções de segundo ciclo (payload real, não o JSON inventado):**

1. Envelope de lote: um POST traz vários pedidos.
2. `created_at` é `LocalDate` `YYYY-MM-DD`, não `Instant` ISO — nasceu o `AlfaDateParser` (`America/Sao_Paulo`).
3. Bug do `.clear()` / lista de itens esvaziada em `criarPedido`: cópia defensiva **antes** de qualquer mutação (documentado em `analise_bug_payload_alfa.md`).
4. `\n` no nome do fornecedor → `StringSanitizer`.

O `DESING.md` original ainda dizia “data Alfa já em ISO”; o `3 ETAPA DESING.md` já corrige isso. O README da raiz ainda mistura H2 (ver §10).

### Etapa 2 — DIA2: Beta (CSV relacional)

Plano em `docs/development/parte-um/beta-alimentos/IMPLEMENTATION-PLAN.md`. Auditoria posterior (`AUDIT-REPORT.md`) confirma: Alfa não foi reescrito (Open/Closed).

Problema central: os CSVs oficiais **não são RFC 4180**. Sem aspas; `\n` no meio do campo (`EM` + `\n` + `ABERTO`; header `PR` + `\n` + `ECO_UNITARIO`). OpenCSV cru quebraria o arquivo.

Pipeline implementado:

```
POST /ingest/beta (multipart: cabecalho + itens)
    → BetaCsvRecordAssembler (máquina de estados + lookahead, N=6 e N=8)
    → OpenCSV @CsvBindByPosition
    → join por NUMERO_PEDIDO
    → BetaPedidoDTO
    → BetaIngestor (CnpjSanitizer, StringSanitizer, BetaStatusMapper, BetaDateParser, BetaNumberParser)
    → PedidoService.upsert (o mesmo do Alfa)
```

Status pt-BR: `EM ABERTO` → `OPEN`, `BLOQUEADO` → `BLOCKED`, `ENCERRADO` → `CLOSED`.  
Número BR: `1.200,000` / `6,49`. Data: `dd/MM/yyyy` → `Instant`.

### Etapa 3 — Fechar regras de conferência (no design, não no código ainda)

O `3 ETAPA DESING.md` fecha as quatro decisões que o `DESING.md` e a análise estrutural deixavam **[EM ABERTO]**:

| # | Decisão | Regra fechada |
|---|---|---|
| 1 | Tolerância de valor | Absoluta **R$ 0,05** com `BigDecimal`; nunca `double` |
| 2 | `BLOCKED` / `CLOSED` | Registram divergência e **continuam** (anti-ioiô) |
| 3 | Material repetido | Soma de `quantidade_pendente` por `codigo_material`; preço **ponderado pelo pendente** |
| 4 | Itens órfãos | Hard delete **condicional**: só se `quantidade_recebida = 0` e o material **não** aparece em `Divergencia` daquele pedido |

`CLAUDE_HELP.md` fecha a consulta: `fornecedor` = CNPJ sanitizado; `com_pendencia` omitido / true / false; sem paginação; lista sem itens; detalhe com `JOIN FETCH`; 200 lista vazia vs 404 no id.

### Etapa 4 — Tentativa conjunta DIA3+DIA4 e correção

`PLAN-CORRECTION-CURSOR.md` registra que uma implementação anterior **preencheu lacunas com decisões próprias**:

- Filtro `fornecedor` com `LIKE` em nome e aliases extras.
- `com_pendencia=false` logicamente nulo (cláusula sempre verdadeira).
- Um DTO de consulta sempre com itens + recálculo Java da pendência.
- JSON camelCase contra o contrato snake_case.
- Órfãos vetados por **qualquer** `Conferencia` do pedido (`existsByPedido_Id`).
- Casca JPA de conferência sem `ConferenciaService` nem endpoints.

A correção alinhou o código às regras já fechadas. Relatório curto: `RELATORIO-IMPLEMENTACAO.md` (chave composta na nota + órfãos + agregação).

### Etapa 5 — Consulta + upsert de órfãos + conferência + relatório (código atual)

Presente no `src/` e nos commits recentes:

- `GET /pedidos` e `GET /pedidos/{id}`
- `removerOrfaos` no `PedidoService`
- Entidades/repositórios `Conferencia` / `Divergencia`
- `ConferenciaService` + `POST /notas-fiscais/conferir` + `GET /relatorios/conferencias`

### Etapa 6 — Ainda não executada

- Gama (DIA5): agrupamento por `ped`, epoch, centavos, `fator_conv`, `UnitConverter`, `POST /ingest/gama`.
- Entregáveis DIA6: README fiel, coleção HTTP, demo, `git tag parte-1`.
- Buffer DIA7.

Não há tag git `parte-1` no repositório neste momento.

---

## 4. Arquitetura alvo vs. arquitetura real

### 4.1 Camadas (conformes)

| Camada | Papel | Implementação |
|---|---|---|
| Controller | Validação e roteamento | `AlfaController`, `BetaController`, `PedidoController`, `ConferenciaController`, `ApiExceptionHandler` |
| DTO de entrada | Espelha `public/` | `dto.alfa`, `dto.beta`; nota em `dto.conferencia` |
| DTO de saída | Contrato unificado snake_case | `dto.pedido`, `ConferenciaResponseDTO`, `RelatorioConferenciasDTO`, `ErroRespostaDTO` |
| Ingestor (Strategy) | DTO → `Pedido` transiente | `PedidoIngestor<T>`, `AlfaIngestor`, `BetaIngestor` |
| Parser (Beta) | Remontagem + OpenCSV + join | `BetaCsvRecordAssembler`, `BetaCsvParser` |
| Normalizadores | CNPJ, status, data, número, string | Alfa + Beta; sem `UnitConverter` |
| Service | Upsert, consulta, conferência | `PedidoService`, `PedidoConsultaService`, `ConferenciaService` |
| Repository | Acesso a dados | JPA; queries de filtro explícitas |
| Postgres | Persistência + coluna gerada | `schema.sql` |

Nenhuma camada de ingestão persiste direto. Conferência não parseia CSV. Consulta não faz upsert. Isso está alinhado ao design.

### 4.2 Padrões

- **Pipes and Filters:** bruto → (parser) → DTO → ingestor/normalizadores → entidade → upsert.
- **Strategy:** um ingestor por cliente, mesmo `PedidoService`.
- **Factory:** não existe, de propósito (rota dedicada + DI do Spring).
- **Open/Closed:** Beta não altera classes do Alfa.

### 4.3 Modelo relacional

Tabelas `Fornecedor`, `Pedido`, `Item`, `Conferencia`, `Divergencia` no DDL **e** no JPA.

- Unicidade de pedido: `(numero_pedido_origem, cliente_origem)`.
- Unicidade de item: `(id_pedido, linha)`.
- Fornecedor único global por CNPJ (Alfa e Beta compartilham o mesmo cadastro se o CNPJ coincidir).
- `quantidade_pendente`: `GENERATED ALWAYS AS (quantidade_pedida - quantidade_recebida) STORED`; JPA `insertable = false, updatable = false`.

---

## 5. Fluxo de execução atual (ponta a ponta)

```
Alfa JSON                         Beta CSV (2 arquivos)
POST /ingest/alfa                 POST /ingest/beta
        │                                  │
 AlfaController                      BetaController
        │                                  │
 AlfaIngestor                    BetaCsvParser → BetaIngestor
        │                                  │
        +----------------+-----------------+
                         │
                  PedidoService.upsert
            1. cópia defensiva dos itens
            2. fornecedor por CNPJ (cria ou atualiza nome)
            3. pedido pela chave origem (cria ou atualiza)
            4. itens por (pedido, linha)
            5. hard delete condicional de órfãos
                         │
                    PostgreSQL
          Fornecedor / Pedido / Item
          Conferencia / Divergencia
                         │
        +----------------+------------------+
        │                │                  │
 GET /pedidos     POST /notas-fiscais/    GET /relatorios/
 GET /pedidos/{id}     conferir            conferencias
 PedidoConsulta     ConferenciaService     (counts no histórico)
```

### Conferência (núcleo)

1. Localiza pedido por `(numero_pedido, cliente_origem)`. Sem pedido: só `PEDIDO_NAO_ENCONTRADO`, HTTP **200**, persiste `REJEITADA`, `id_pedido` nulo.
2. CNPJ da nota via `CnpjSanitizer`; compara com o do fornecedor do pedido.
3. Se `BLOCKED` ou `CLOSED`, empilha divergência e segue.
4. Agrupa linhas da nota e do pedido por `codigo_material`.
5. Material ausente → `MATERIAL_NAO_ENCONTRADO`.
6. Quantidade da nota (soma das linhas daquele material) vs soma de `quantidade_pendente`.
7. Valor: `|valor_nota − (qtd × preço ponderado)| > 0,05` → `VALOR_DIVERGENTE`.
8. Persistência única no fim: `APROVADA` se lista vazia, senão `REJEITADA` + uma `Divergencia` por falha.

---

## 6. Implementações concretizadas

### 6.1 Ingestão Alfa

| Peça | Arquivo / contrato |
|---|---|
| Endpoint | `POST /ingest/alfa` |
| DTOs | `AlfaPayloadDTO`, `AlfaPedidoDTO`, `AlfaFornecedorDTO`, `AlfaItemDTO` |
| Ingestor | `AlfaIngestor` (`clienteOrigem = "ALFA"`) |
| Normalização | CNPJ, string, status `open/closed/blocked`, data civil → `Instant` |

### 6.2 Ingestão Beta

| Peça | Arquivo / contrato |
|---|---|
| Endpoint | `POST /ingest/beta` (`multipart/form-data`: `cabecalho`, `itens`) |
| Remontagem | `BetaCsvRecordAssembler` |
| Parse + join | `BetaCsvParser` |
| DTOs | linhas OpenCSV + `BetaPedidoDTO` / `BetaItemDTO` |
| Ingestor | `BetaIngestor` (`clienteOrigem = "BETA"`) |
| Normalização | `BetaNumberParser`, `BetaDateParser`, `BetaStatusMapper` |

### 6.3 Persistência compartilhada

- `PedidoService.upsert` transacional.
- Cópia defensiva da lista de itens.
- `removerOrfaos`: apaga linha sumida do arquivo só sem recebimento e sem `Divergencia` daquele `codigo_material` no pedido. Uma conferência só de `FORNECEDOR_DIVERGENTE` **não** congela todas as linhas.

### 6.4 Consulta

| Método | Comportamento |
|---|---|
| `GET /pedidos` | Filtros opcionais combináveis: `cliente_origem`, `fornecedor` (CNPJ, sanitizado), `status`, `com_pendencia`. Lista = resumo **sem itens**. Vazio = 200. Sem paginação. |
| `GET /pedidos/{id}` | Detalhe com fornecedor e itens; `quantidade_pendente` lida do banco, sem recálculo. Inexistente = **404** com `ErroRespostaDTO`. |
| JPQL | Três queries: sem cláusula de pendência; `EXISTS` pendente > 0; `NOT EXISTS`. Lista faz `JOIN FETCH` só de fornecedor. Detalhe faz fetch de fornecedor e itens. |

JSON de saída em snake_case (`id_pedido`, `numero_pedido`, …), alinhado à seção 2 do design.

### 6.5 Conferência e relatório

| Peça | Estado |
|---|---|
| Entidades | `Conferencia` (`id_pedido` opcional), `Divergencia` (`valor_esperado` / `valor_recebido` como `TEXT` / `String`) |
| Enums | `ResultadoConferencia`, `TipoDivergencia` (os 7 tipos do design) |
| DTO da nota | `numero_pedido` + **`cliente_origem` obrigatório** + `fornecedor_cnpj` + itens (`quantidade` / `valor_total`, `@DecimalMin("0")`) |
| Endpoints | `POST /notas-fiscais/conferir`, `GET /relatorios/conferencias` |
| HTTP | Payload inválido = 400; pedido inexistente = 200 de negócio |

### 6.6 Infraestrutura

- Java 21, Spring Boot 4.1.1, OpenCSV 5.12.0, Lombok, Validation, Postgres driver.
- `Dockerfile` multi-stage; `compose.yaml`.
- Testes: unitários (`new` / Mockito) + alguns `@WebMvcTest` de controller. **Não** há suíte de integração contra Postgres para coluna gerada.

Contagem aproximada da suíte atual: **~129** métodos `@Test` (Alfa ~42 originais + Beta parser/ingestor + consulta + conferência + órfãos).

### 6.7 O que o `3 ETAPA DESING.md` ainda marca [A FAZER] e o código já fez

| Item no design operacional | Código |
|---|---|
| `BetaIngestor`, `POST /ingest/beta`, parsers BR | Feito |
| `GET /pedidos` e detalhe | Feito |
| Entidades JPA Conferencia/Divergencia | Feito |
| `ConferenciaService` + endpoints + relatório | Feito |
| Hard delete condicional de órfãos | Feito |
| `GamaIngestor`, `UnitConverter`, `POST /ingest/gama` | **Não** |
| `git tag parte-1` | **Não** |

---

## 7. Estratégias tomadas para o sistema funcionar

Estas são as escolhas que **explicam** o comportamento de hoje — não apenas a lista de classes.

1. **Contrato único na persistência, formatos brutos na borda.** Sem isso, conferência e relatório teriam de conhecer CSV e JSON aninhado.
2. **Chave `(numero + cliente_origem)` em ingestão, consulta implícita e conferência.** Dois ERPs podem repetir o número; a nota **obriga** `cliente_origem` para não casar o pedido errado.
3. **Ingestor sem banco.** Permite testar tradução com `new` e reusar o mesmo upsert.
4. **Parsers de data por cliente, sem interface `DateParser`.** Formatos incompatíveis (`YYYY-MM-DD`, `dd/MM/yyyy`, epoch). O design operacional autoriza isso.
5. **Pré-processador Beta por contagem de `;`, não por conteúdo.** Evita regra que só funciona nos dois arquivos de exemplo.
6. **Coluna gerada no banco.** Uma única definição de pendência; ingestores não escrevem o campo.
7. **Lista acumulada na conferência.** Diagnóstico completo na primeira passagem (requisito de relatório por motivo).
8. **Tolerância absoluta R$ 0,05.** Antecipação do Gama (`fator_conv` / centavos) e de dízimas em `BigDecimal`.
9. **Agregação por SKU, não FIFO por linha.** A nota não cita `linha` interna do pedido.
10. **HTTP 200 para “pedido não encontrado” na conferência** vs **404** no `GET /pedidos/{id}`: rejeição de negócio ≠ recurso REST ausente.
11. **Órfãos olhando recebimento + divergência do material**, não “qualquer conferência do pedido”, para não deixar pendência fantasma nem apagar histórico fiscal.
12. **Postgres real + testes unitários rápidos.** Troca consciente: a coluna gerada não é exercitada ponta a ponta na suíte (o README admite).

---

## 8. Validação: o que está sólido

- Separação de responsabilidades na ingestão e no upsert.
- Strategy + rotas dedicadas adequadas ao tamanho do desafio.
- Schema com constraints e coluna gerada coerente com as entidades financeiras (`NUMERIC` / `BigDecimal`).
- Decisões de conferência no código batem com as seções 3.5.1 e 5.3 do `3 ETAPA DESING.md`.
- Consulta corrigida: CNPJ sanitizado, três queries de pendência, DTOs lista vs detalhe, snake_case.
- Testes de regressão para bugs reais (cópia de itens, header CSV partido, tolerância no limite 0,05, agregação de duas linhas, BLOCKED + outras falhas).
- Fornecedor único por CNPJ entre clientes — correto para o ecossistema V360.

A arquitetura **cumpre o propósito** da Parte 1: normalizar Alfa e Beta e conferir nota contra o modelo único.

---

## 9. Estratégias mal elaboradas ou frágeis na prática

Não são “erros de compilação”; são pontos em que a prática de software ou o desenho operacional ficam frouxos.

### 9.1 Documentação operacional não versionada com o código

O arquivo que deveria ser a âncora (`3 ETAPA DESING.md`) e os `DIA*.md` **não foram atualizados** após Beta e conferência. Quem seguir só o design operacional vai reimplementar o que já existe. A análise estrutural de 06/09 descreve um sistema só-Alfa. Isso é a mesma classe de falha relatada no `AI_USAGE.md` (IA gerando payload genérico em vez de ler `public/`): **fonte de verdade duplicada e velha**.

### 9.2 `orphanRemoval = true` ainda no `Pedido`

O design pede delete **explícito e condicional** e avisa que a cascata **não** substitui a regra. O upsert atual não faz `.clear()` no pedido existente, então o comportamento desejado prevalece. A anotação continua sendo armadilha: qualquer refatoração que sincronize a coleção `itens` pode apagar linhas movimentadas. Estratégia incompleta: a regra está no service, a JPA ainda sugere o contrário.

### 9.3 Conferência não atualiza saldo (coerente com o README, opaco no domínio)

Após `APROVADA`, `quantidade_recebida` permanece a da última ingestão. Duas notas aprovadas seguidas contra o mesmo pendente **passam as duas**, porque nada consome o saldo. Se o desafio pede só “bater nota × pedido e historiar”, está certo. Se o operador espera que conferência = recebimento, o modelo está incompleto. Vale deixar explícito na entrevista: **fonte de recebimento = ERP (reingestão), não a NF da V360**.

### 9.4 Conferência carrega itens via LAZY na sessão, sem `JOIN FETCH`

`findByNumeroPedidoOrigemAndClienteOrigem` não busca itens/fornecedor. Funciona dentro de `@Transactional` (lazy load). É N+1 e depende da sessão aberta. A consulta de detalhe já aprendeu essa lição; a conferência não replicou o `JOIN FETCH`. Risco baixo hoje, frágil se alguém marcar o método `readOnly` errado ou serializar a entidade fora da transação.

### 9.5 Testes da conferência mockam `quantidade_pendente`

Os testes unitários **setam** o campo gerado no objeto. Em produção o valor só existe depois de ler o Postgres. Não há teste de integração que prove o cruzamento com a coluna `GENERATED`. A suíte pode ficar verde com um mapeamento JPA quebrado nesse campo.

### 9.6 Erros de negócio do Beta viram 500

`BetaCsvParser` / mappers lançam `IllegalArgumentException` (item sem cabeçalho, número BR inválido, status desconhecido). `ApiExceptionHandler` só trata `ResponseStatusException` e Bean Validation. Join inválido ou CSV ilegível tende a **500**, não 400 padronizado. Alfa, com `@Valid` no JSON, fica mais previsível que o multipart do Beta.

### 9.7 `CREATE TABLE IF NOT EXISTS` sem migração

Schema inicial correto. Evolução de colunas em volume Docker já criado **não aplica**. Estratégia aceitável no desafio, ruim como disciplina de evolução.

### 9.8 H2 no classpath e no README, Postgres na prática

`pom.xml` traz H2 (`runtime`) e `spring-boot-h2console`. `application.properties` só aponta Postgres via env. README ainda diz que H2 é o padrão local. Dois bancos mentais: quem seguir o README não sobe o mesmo sistema que o Compose.

### 9.9 JDBC no Compose usa `${POSTGRES_PORT}` no host interno `postgres`

O Postgres **dentro** da rede Docker escuta 5432. Se `.env` mapear a porta do host para outro valor e a URL da `app` reutilizar essa variável, a app no container pode falhar. Host da máquina (`localhost:${POSTGRES_PORT}`) e hostname `postgres:5432` são contratos diferentes; o `compose.yaml` mistura os dois.

### 9.10 Relatório é contagem bruta do histórico

Cada POST de conferência insere uma linha. Reprocessar a mesma nota infla totais. Não há idempotência nem “última conferência por pedido”. Adequado a um desafio curto; frágil como métrica operacional.

### 9.11 Tentativa de avançar DIA3 e DIA4 juntos

O `PLAN-CORRECTION-CURSOR.md` é evidência de estratégia de implementação ruim: preencher buracos do enunciado em vez de implementar decisões já fechadas. O código atual parece corrigido; o processo custou retrabalho e divergência temporária (LIKE no nome, veto global de órfãos, recálculo de pendente).

### 9.12 Gama ainda não existe, mas a conferência já assume o mundo Gama

A tolerância de R$ 0,05 e o `UnitConverter` futuro estão no desenho. Sem Gama, a Parte 2 do desafio (o que só se *adicionou* vs. o que *mexeu* no existente) ainda não pode ser respondida com evidência.

---

## 10. Dados e contratos inconsistentes

| Onde | Inconsistência |
|---|---|
| README vs `application.properties` | README: H2 padrão. Código: só Postgres + env. |
| README vs design | README já descreve Beta, consulta e conferência. `3 ETAPA DESING.md` e `DIA2`–`DIA4` ainda “pendente”. |
| `DESING.md` (raiz e `docs/desing/`) vs `3 ETAPA DESING.md` | Original ainda fala Factory secundária, DateParser genérico, decisões [EM ABERTO]. Operacional já fechou as quatro regras e dispensou Factory. |
| `ANALISE_ESTRUTURAL_PROJETO.md` | Congelada em 06/09: sem Beta, sem JPA de conferência, decisões em aberto. |
| Kanban `docs/tasks/` | Só DIA1 checked; o git já tem Beta, filtros e conferência. |
| Resposta do ingest vs consulta | Ingest devolve `numero_pedido_origem`; consulta devolve `numero_pedido`. Mesmo dado, nomes diferentes. |
| `Conferencia.fornecedor_cnpj` VARCHAR(20) vs `Fornecedor.cnpj` VARCHAR(255) | Após sanitizar cabe em 14 dígitos; os tipos DDL não são iguais. |
| Agregação na nota | Várias linhas da NF do mesmo SKU são somadas antes de comparar. O design fala “quantidade da nota”; o código trata a nota também como agregado. É razoável, mas não está escrito no §5.3. |
| `PLAN-CORRECTION` vs DTOs atuais | O plano reclamava `DivergenciaDTO` em `BigDecimal` e `@Positive`. O código atual usa `String` e `@DecimalMin("0")` — a correção foi feita; o plano de correção ficou como diagnóstico histórico. |

Nenhuma dessas quebra o happy path Alfa/Beta se o operador usar os endpoints certos e o Compose com porta 5432. Elas quebram **onboarding, entrevista e a próxima fatia (Gama)** se alguém usar o documento errado como spec.

---

## 11. Documentação desatualizada (dívida documental)

Para o próximo ciclo (Gama / tag `parte-1`), a ordem honesta de atualização seria:

1. `docs/desing/3 ETAPA DESING.md` — marcar Beta, consulta, conferência, órfãos e relatório como **[FEITO]**; deixar Gama e tag como **[A FAZER]**.
2. `docs/tasks/DIA2.md`, `DIA3.md`, `DIA4.md` — checkboxes alinhados ao `src/`.
3. README — remover H2 como runtime padrão; documentar Postgres + `.env`; opcionalmente unificar `numero_pedido` na resposta do ingest.
4. `docs/ANALISE_ESTRUTURAL_PROJETO.md` — tratar como arquivo histórico (Alfa-only) ou substituir por este documento.
5. `AI_USAGE.md` — o próprio arquivo corta no meio (“Pedi para ser gerado o”). Completar no dia, como o processo pedia.

Este arquivo (`FLUXO_LOGICO_IMPLEMENTACAO.md`) descreve o estado de **08/09/2026**. Se o Gama entrar, ele precisa de um adendo; não deve ser editado como se o Gama já existisse.

---

## 12. O que ainda falta (honesto)

Alinhado ao `3 ETAPA DESING.md` **depois** de descontar o que o código já fez:

| Item | Status |
|---|---|
| Ingestão Gama + `UnitConverter` + testes + README “só adicionar vs. mexer” | Pendente (DIA5 / Parte 2) |
| Coleção `.http` / Postman, demo, README 100% fiel | Pendente (DIA6) |
| `git tag parte-1` | Pendente (requisito do enunciado; só após Parte 1 estável) |
| Testes de integração Postgres (coluna gerada, JPQL de `com_pendencia`) | Ausentes; citados no README como “o que faria diferente” |
| Handler 400 para erros de parse/join do Beta | Ausente |
| Paginação | Fora do escopo mínimo (decisão consciente) |
| Atualizar `quantidade_recebida` na conferência | Fora do desenho atual (intencional) |

Prioridade se o calendário apertar (já no design operacional): não cortar consulta, conferência, relatório nem regras no README. Cortar antes: testes extensos, paginação, vídeo, Docker “só local”.

---

## 13. Conclusão

O projeto foi conduzido como **normalizador + conferidor**, não como ERP. O fluxo de trabalho (design → fatia → bug em payload real → segundo ciclo → correção quando a IA inventou regra) é visível em `docs/` e no `src/`.

**O que funciona hoje:** ingestão Alfa e Beta no mesmo upsert, consulta filtrada no contrato unificado, conferência com as quatro decisões fechadas, relatório sobre histórico persistido, Postgres com pendência gerada.

**O propósito está atendido para a Parte 1**, com arquitetura em camadas e Strategy coerentes.

**O maior risco atual não é a conferência em si** — é a **documentação operacional atrasada em relação ao código**, mais alguns atalhos (H2 no README, 500 no CSV Beta, testes sem Postgres, `orphanRemoval` residual, conferência sem consumir saldo). Quem implementar o Gama a partir do `3 ETAPA DESING.md` sem ler este arquivo vai achar que Beta e DIA4 ainda não existem.

Gama permanece o único cliente do diagrama original fora do código; a tag `parte-1` ainda não foi criada.
