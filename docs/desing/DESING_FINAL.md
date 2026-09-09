# V360 — Conector de Pedidos de Compra
## Cópia de trabalho do design (alinhada ao que falta fazer)

Este arquivo é a **cópia operacional** do `DESING.md`. Foi ajustada com base na [análise estrutural do projeto](../development/ANALISE_ESTRUTURAL_PROJETO.md) (06/09/2026): corrige trechos desatualizados, marca o que já está no código e deixa explícito o que ainda precisa ser implementado.

**Atualização de 08/09/2026:** sincronizada com [`docs/FLUXO_LOGICO_IMPLEMENTACAO.md`](../development/FLUXO_LOGICO_IMPLEMENTACAO.md), que auditou o `src/` e constatou que a Parte 1 (Alfa + Beta + consulta + conferência + relatório) está implementada.

**Atualização de 09/09/2026:** Parte 2 (Gama) implementada conforme [`CURSOS_FINAL_IMPLEMENTATION_PLAN.md`](../development/parte-dois/gama/CURSOS_FINAL_IMPLEMENTATION_PLAN.md) — só **adicionar** (DTO de linha + DTO interno + agrupador + normalizadores + ingestor + controller + testes). Alfa, Beta, conferência, schema e `PedidoService` **não** foram alterados. Restam a tag `parte-1` e os entregáveis do DIA6.

As quatro decisões de negócio que estavam em aberto (**tolerância de preço**, **BLOCKED/CLOSED**, **material duplicado**, **itens órfãos**) estão **fechadas** nas seções 3.5 e 5.3 e **implementadas no código**. Pontos **[FEITO]** não devem ser refeitos; devem ser reutilizados.

---

## 0. Payloads de exemplo dos clientes (`public/`)

Os **modelos reais de payload** usados para desenhar DTOs, ingestores e testes **não estão neste documento**. Eles vivem em `public/`, nomeados pela empresa. Use esses arquivos como fonte da verdade do formato bruto — o DTO de cada cliente deve espelhá-los, não o contrato JSON unificado da seção 2.

| Empresa | Formato | Arquivo(s) | Como usar |
|---|---|---|---|
| **Alfa Energia** | JSON aninhado (lote em `purchase_orders`) | [`public/alfaEnergiaPayload.json`](../../public/alfaEnergiaPayload.json) | Corpo de `POST /ingest/alfa`. Coberto por `AlfaPayloadDTO` / `AlfaIngestor`. **[FEITO]** |
| **Beta Alimentos** | CSV (2 arquivos, `;`, padrão BR) | [`public/betaAlimentos/cabecalho.csv`](../../public/betaAlimentos/cabecalho.csv) e [`public/betaAlimentos/itens.csv`](../../public/betaAlimentos/itens.csv) | Entrada de `POST /ingest/beta`. Join por `NUMERO_PEDIDO`. **[FEITO]** |
| **Gama Logística** | JSON achatado (array: 1 objeto = 1 linha de item) | [`public/gamaLogisticaPayload.json`](../../public/gamaLogisticaPayload.json) | Corpo de `POST /ingest/gama` (`List<@Valid GamaItemLinhaDTO>`). Agrupar por `ped` em `GamaPayloadAgrupador`. **[FEITO]** |

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
    AlfaIngestor       BetaCsvParser → BetaIngestor     GamaPayloadAgrupador → GamaIngestor  <- Strategy
     [FEITO]                  [FEITO]                  [FEITO]
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

Os três clientes seguem o mesmo desenho. Gama **repetiu o padrão** (controller + DTOs + parser/agrupador + ingestor) **sem alterar** Alfa ou Beta (Open/Closed).

### 1.2 Camadas e responsabilidades

| Camada | Responsabilidade | Não faz | Estado |
|---|---|---|---|
| Controller | Validação (`@Valid`), roteamento | Lógica de negócio | Alfa, Beta e Gama **feitos** |
| DTO de entrada (por cliente) | Espelha o payload bruto em `public/` | Normalização | `dto.alfa`, `dto.beta` e `dto.gama` (`GamaItemLinhaDTO` HTTP; `GamaPedidoDTO` interno) **feitos** |
| Ingestor (Strategy) | DTO já aninhado → entidade de domínio | Persistência / agrupamento | `AlfaIngestor`, `BetaIngestor` e `GamaIngestor` (`PedidoIngestor<GamaPedidoDTO>`) **feitos** |
| Parser (Beta / Gama) | Join / agrupamento → lista de pedidos | Persistência | `BetaCsvRecordAssembler`, `BetaCsvParser` **feitos**; `GamaPayloadAgrupador` **feito** (por `ped` + consistência de cabeçalho) |
| DTO de saída | Contrato unificado snake_case | — | `dto.pedido`, `ConferenciaResponseDTO`, `RelatorioConferenciasDTO`, `ErroRespostaDTO` **feitos** |
| Normalizadores | CNPJ, status, data, string, unidade | Persistência | Alfa + Beta + Gama — ver 1.4 |
| Service | Upsert, consulta, conferência | SQL nativo | `PedidoService`, `PedidoConsultaService`, `ConferenciaService` **feitos** |
| Repository | Acesso a dados | Lógica de negócio | Todos os repositórios (incluindo `Conferencia`/`Divergencia`) **feitos** |
| Entidade / Postgres | Persistência | — | Todas as 5 entidades **feitas** |

### 1.3 Padrões

- **Strategy**: interface `PedidoIngestor<T>` já existe. `AlfaIngestor`, `BetaIngestor` e `GamaIngestor` **feitos**.
- **Open/Closed**: Beta não alterou classes do Alfa. Gama também não alterou Alfa/Beta (nem `StatusMapper`, conferência, schema ou `PedidoService`).
- **Factory**: não implementar. Rotas dedicadas `/ingest/alfa`, `/ingest/beta`, `/ingest/gama`.
- **Pipes and Filters**: bruto → (parser) → DTO → ingestor/normalizadores → entidade → upsert.

### 1.4 Normalizadores — o que reutilizar vs. o que criar

| Peça | Estado | Uso |
|---|---|---|
| `CnpjSanitizer` | **Feito** (estático, compartilhado) | Alfa, Beta e Gama reutilizam (`sanitize`) |
| `StringSanitizer` | **Feito** (não estava no design original; surgiu do `\n` no payload Alfa) | Gama reutiliza em nome do fornecedor e `desc_mat` |
| `StatusMapper` (interface) + `AlfaStatusMapper` | **Feito** | Contrato compartilhado: `map(String)`. **Não** genérica — Gama não implementa a interface (Open/Closed) |
| `BetaStatusMapper` | **Feito** — `EM ABERTO` → `OPEN`, `BLOQUEADO` → `BLOCKED`, `ENCERRADO` → `CLOSED` | Não reimplementar |
| `GamaStatusMapper` | **Feito** — `@Component`, `map(Integer)`: `1→OPEN`, `2→CLOSED`, `3→BLOCKED`; outro/null → `IllegalArgumentException` | Assimetria consciente: payload chega `Integer`, não `String` |
| `AlfaDateParser` | **Feito** — `LocalDate` `YYYY-MM-DD` → `Instant` (`America/Sao_Paulo`) | Específico do Alfa. |
| `BetaDateParser` | **Feito** — `dd/MM/yyyy` → `Instant` | Específico do Beta. |
| `BetaNumberParser` | **Feito** — número BR (`1.200,000` / `6,49`) → `BigDecimal` | Específico do Beta. |
| `GamaDateParser` | **Feito** — estático, `parse(Long)` → `Instant.ofEpochSecond`; null-safe | `1786752000` → `2026-08-15T00:00:00Z` (teste dourado). Isolado, sem interface `DateParser`. |
| `UnitConverter` | **Feito** (estático) | `converterQuantidade(qtd, fator) = qtd × fator` (pedida **e** recebida); `converterPrecoUnitario(centavos, fator) = (centavos/100) / fator` (`HALF_UP`, escala intermediária 10, escala final 4) |

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

### Decisões de normalização (vigentes nos três clientes)

- **CNPJ**: só dígitos no contrato único. Sempre via `CnpjSanitizer`.
- **Status**: enum `{ OPEN, CLOSED, BLOCKED }`. Vocabulário de origem: Alfa `open/closed/blocked`; Beta `"EM ABERTO"` / `"BLOQUEADO"` (e equivalentes no CSV); Gama `1/2/3`. Cada cliente tem o próprio mapper; a saída é o mesmo enum.
- **Unidade de medida**: persistir a **unidade em que a nota informa** (não a unidade de compra, ex. caixa). No Gama: converter via `fator_conv` e gravar `"UN"`; o campo `um` do JSON não vai ao banco. Documentar no README.
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
`Fornecedor.cnpj` é `UNIQUE` global. O mesmo CNPJ no Alfa, Beta e Gama é a mesma entidade. O `PedidoService` já faz upsert por CNPJ (atualiza nome se existir). Documentar no README.

### 3.3 Tipagem financeira
`NUMERIC(15,4)` / `BigDecimal` — já nas entidades. Gama converte centavos e `fator_conv` no `UnitConverter` (escala 4, `HALF_UP`) antes do upsert.

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
Só (se necessário) para testes que **não** exercitam upsert real nem coluna gerada. Não usar H2 como banco de integração de persistência. A suíte é unitária: ingestores/parsers/normalizadores/services com `new` + Mockito; controllers de consulta/conferência com `@WebMvcTest` (slice, sem Postgres). Ver §7.

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

### 5.5 Tratamento de exceções — **[FEITO]**

Contrato de erro: `ErroRespostaDTO` (`status` + `mensagem`). Centralizado em `ApiExceptionHandler` (`@RestControllerAdvice`).

| Exceção | HTTP | Corpo |
|---|---|---|
| `ResponseStatusException` | o status da própria exceção | `reason` (ex.: `GET /pedidos/{id}` inexistente → **404** `"pedido não encontrado"`; filtro `status` inválido no `PedidoController` → **400** `"status inválido"`) |
| `MethodArgumentNotValidException` | **400** | campos Bean Validation concatenados (`campo: mensagem; ...`); se vazio, `"payload inválido"` |
| `IllegalArgumentException` | **400** | `ex.getMessage()` |
| `HttpMessageNotReadableException` | **400** | `"Corpo da requisição ausente ou JSON inválido."` (mensagem fixa; não vaza o parse do Jackson) |
| `MethodArgumentTypeMismatchException` | **400** | parâmetro + tipo esperado (ex.: UUID inválido no path) |
| qualquer outra `Exception` | **500** | `"Erro interno inesperado."` (stack só no log) |

**`IllegalArgumentException` → 400** cobre parse/join do Beta (item sem cabeçalho, número BR inválido, data BR inválida, status desconhecido), mappers Alfa/Gama com status inválido, lista Gama vazia/nula e cabeçalho divergente no mesmo `ped`. A nota antiga de que isso virava **500** está **obsoleta**.

O que **não** é 400: `UncheckedIOException` na leitura do CSV Beta (e equivalentes de I/O) cai no handler genérico → **500**. Não há handler de `ConstraintViolationException` (validação fora de `@RequestBody`/`@Valid` clássico).

---

## 6. Particularidades por cliente (ingestão)

Fonte de formato: seção 0 (`public/`).

| Cliente | Payload | Conversões | Estado |
|---|---|---|---|
| **Alfa Energia** | [`public/alfaEnergiaPayload.json`](../../public/alfaEnergiaPayload.json) | `created_at` `LocalDate` → `Instant` (`AlfaDateParser`); CNPJ; status `open/closed/blocked`; `StringSanitizer` no nome do fornecedor | **[FEITO]** e testado (ver §7). Não reimplementar. |
| **Beta Alimentos** | [`public/betaAlimentos/cabecalho.csv`](../../public/betaAlimentos/cabecalho.csv) + [`public/betaAlimentos/itens.csv`](../../public/betaAlimentos/itens.csv) | `BetaCsvRecordAssembler` (máquina de estados); OpenCSV `@CsvBindByPosition`; join por `NUMERO_PEDIDO`; `BetaNumberParser` (número BR); `BetaDateParser` (`dd/MM/yyyy`); `CnpjSanitizer`; `BetaStatusMapper` | **[FEITO]** — `POST /ingest/beta` (`multipart/form-data`). Alfa não foi alterado (Open/Closed). |
| **Gama Logística** | [`public/gamaLogisticaPayload.json`](../../public/gamaLogisticaPayload.json) | Array JSON raiz (sem wrapper); `GamaPayloadAgrupador` por `ped` (`LinkedHashMap`) + consistência de cabeçalho (cnpj, nome, `dt_criacao`, `situacao`); `GamaDateParser` epoch segundos; `GamaStatusMapper` 1/2/3; `quantidade_pedida = qtd_ped × fator_conv`; `quantidade_recebida = qtd_rec × fator_conv`; `preco_unitario = (preco_unit_centavos/100) ÷ fator_conv`; persistir `"UN"` (campo `um` é unidade de compra, não vai ao banco); moeda `"BRL"` constante; `cliente_origem = "GAMA"` | **[FEITO]** — `POST /ingest/gama` (`List<@Valid GamaItemLinhaDTO>`), `GamaPedidoDTO` interno, `GamaIngestor`, `UnitConverter`. Testes unitários (DTO, agrupador, parsers, converter, ingestor). Alfa/Beta não alterados. |

**Correção em relação ao `DESING.md` original:** não tratar a data do Alfa como "já ISO, sem conversão". O arquivo `alfaEnergiaPayload.json` envia só `YYYY-MM-DD`.

**Parte 2 (Gama) — o que foi só adicionar:** `GamaItemLinhaDTO`, `GamaPedidoDTO`, `GamaPayloadAgrupador`, `GamaDateParser`, `GamaStatusMapper`, `UnitConverter`, `GamaIngestor`, `GamaController` e testes. **Nada exigiu mexer** no que já existia. No README (DIA6), registrar isso; o desafio pede explicitamente.

**Lacuna da tabela antiga desta seção:** o JSON da seção 2 já mostrava `quantidade_recebida` 24 (= `2 × 12`). A ingestão **converte também `qtd_rec`**, não só `qtd_ped`.

##### Decisões Gama (fechadas e implementadas)

- **Body HTTP:** array de linhas (`GamaItemLinhaDTO`), não wrapper. Bean Validation no argumento de tipo (`List<@Valid ...>`). `fator_conv` `@Positive` (evita divisão por zero). `qtd_rec` `@PositiveOrZero` (o payload oficial tem `0`). Status inválido cai no mapper, não em `@Min/@Max`.
- **Agrupador fora do ingestor:** mesmo papel do join do Beta. `GamaIngestor` só faz `toEntity(GamaPedidoDTO)` — sem `toEntities`. Lista nula/vazia ou cabeçalho divergente no mesmo `ped` → `IllegalArgumentException` (400 via `ApiExceptionHandler`).
- **Unidade persistida:** sempre `"UN"` após conversão. Hardcode do desafio para o Gama, não tabela `CX→UN` genérica.
- **`GamaStatusMapper` não implementa `StatusMapper`:** a interface é `map(String)`; tornar genérica tocaria Alfa/Beta.

**Teste dourado (payload oficial):** item `TRP-01` / `GL-778` → pedida 120, recebida 24, pendente 96 (coluna gerada no banco), preço `100.00`, `UN`, `OPEN`. `TRP-09` preço `33.3333`. `GL-779` `CLOSED`, 100/100.

Parse/join inválido (Beta e Gama) lança `IllegalArgumentException` e o `ApiExceptionHandler` devolve **400** (`ErroRespostaDTO`). Ver §5.5.

---

## 7. Testes unitários — **[FEITO]**

Cerca de **150** métodos `@Test` em **22** classes sob `src/test/java`. Sem Spring Context na ingestão; sem banco real (a coluna `GENERATED` de `quantidade_pendente` **não** é exercitada — os testes de conferência setam o campo no objeto). Payloads oficiais em `public/` entram nos testes de parser/DTO/ingestor.

### 7.1 Estilo

| Tipo | Como | Onde |
|---|---|---|
| Puro | `new` da classe + AssertJ | parsers, normalizadores, DTOs, ingestores |
| Mockito | repositórios mockados | `PedidoService`, `PedidoConsultaService`, `ConferenciaService` |
| Slice MVC | `@WebMvcTest` + `ApiExceptionHandler` | `PedidoControllerTest`, `ConferenciaControllerTest` |

Gama segue o mesmo padrão do Alfa/Beta (`new GamaIngestor(new GamaStatusMapper())`, sem `@SpringBootTest`).

### 7.2 Cobertura por fatia

| Fatia | Classes | O que prova |
|---|---|---|
| **Alfa** | `AlfaIngestorTest`, `AlfaStatusMapperTest`, `AlfaDateParserTest` | payload → entidade; CNPJ; status case-insensitive; `LocalDate` → meia-noite `America/Sao_Paulo`; `\n` no nome/descrição; status inválido → IAE |
| **Beta** | `BetaCsvRecordAssemblerTest`, `BetaCsvParserTest`, `BetaIngestorTest`, `BetaNumberParserTest`, `BetaStatusMapperTest` | CSV não RFC 4180; payload oficial (2 pedidos); join por `NUMERO_PEDIDO`; número BR; `EM ABERTO`/`BLOQUEADO`/`ENCERRADO`; item órfão de cabeçalho → IAE; `quantidadePendente` **não** setada no ingestor |
| **Gama (Parte 2)** | `GamaItemLinhaDTOTest`, `GamaPayloadAgrupadorTest`, `GamaDateParserTest`, `GamaStatusMapperTest`, `UnitConverterTest`, `GamaIngestorTest` | 3 linhas / 13 campos do JSON oficial; agrupamento GL-778 (2 itens) + GL-779 (1); cabeçalho divergente / lista vazia → IAE; epoch `1786752000` → `2026-08-15T00:00:00Z`; 1/2/3; TRP-01 **120 / 24 / 100.0000**; TRP-09 **33.3333**; GL-779 CLOSED 100=100 |
| **Compartilhado** | `CnpjSanitizerTest`, `StringSanitizerTest` | máscara/espaços; `\n` `\r` `\t` + trim |
| **Upsert** | `PedidoServiceTest` | fornecedor novo vs. nome atualizado; pedido/item create vs. update; órfão sem histórico **remove**; órfão com recebimento ou divergência do material **preserva** |
| **Consulta** | `PedidoConsultaServiceTest`, `PedidoControllerTest` | filtros isolados e combinados; CNPJ sanitizado; `com_pendencia`; lista sem itens; detalhe usa pendente do banco; 200 vazio; 404; 400 `status` inválido |
| **Conferência** | `ConferenciaServiceTest`, `ConferenciaControllerTest`, `ConferenciaMappingTest` | todos os tipos de divergência; tolerância R$ 0,05 no limite; `BLOCKED`/`CLOSED` registram e **continuam**; agregação por material + preço ponderado; relatório de contagens; `POST /conferir` 200 mesmo com pedido inexistente; payload inválido 400 |

### 7.3 O que a suíte não cobre (não inventar)

- Controllers de ingestão (`AlfaController`, `BetaController`, `GamaController`) — sem `@WebMvcTest`.
- `ApiExceptionHandler` isolado — só de passagem nos slices de consulta/conferência.
- `BetaDateParser` — sem classe de teste própria (data Beta entra via parser/ingestor).
- Integração com Postgres (upsert real, coluna `GENERATED`, N+1 da conferência).

---

## 8. O que falta fazer (Kanban honesto)

Estado em 09/09/2026. Alfa, Beta, consulta, conferência, relatório e **Gama** implementados. Não reabrir essas fatias salvo bug.

| Dia | Escopo | Status | O que de fato falta |
|---|---|---|---|
| DIA1 | Setup + ingestão Alfa | **Feito** | — |
| DIA2 | Ingestão Beta | **Feito** | — |
| DIA3 | Consultas | **Feito** | — |
| DIA4 | Conferência + relatório + tag | **Parcialmente feito** | `ConferenciaService`, entidades, endpoints feitos. Falta: `git tag parte-1` |
| DIA5 | Gama (Parte 2) | **Feito** | — |
| DIA6 | Entregáveis | **Pendente** | README fiel (remover H2 como padrão; documentar Postgres + `.env`; unificar `numero_pedido`; anotar Gama = só adicionar); coleção `.http` / Postman; demo; `AI_USAGE.md` completo |
| DIA7 | Buffer | **Pendente** | Revisão |

---

## 9. Checklist de pontos a não esquecer
- Payloads oficiais: `public/alfaEnergiaPayload.json`, `public/betaAlimentos/cabecalho.csv`, `public/betaAlimentos/itens.csv`, `public/gamaLogisticaPayload.json`.
- Anotar prompts no `AI_USAGE.md` **no dia**, não de memória no fim. O arquivo atual está incompleto (cortado no meio).
- `git tag parte-1` é requisito do enunciado — ainda não criada.
- `UnitConverter` no Gama **já converte** pedida **e** recebida; conferência do Gama usa unidade da nota. Não reabrir.
- Conferência: tolerância **R$ 0,05**; lista completa de divergências (sem `return` em `BLOCKED`/`CLOSED`); cruzar por **soma de pendente por `codigo_material`**. Tudo isso já está implementado — não alterar.
- Testes unitários (~150) — ver §7. Gama foi só adicionar; Alfa/Beta não devem quebrar. Não há suíte de integração Postgres.
- Levar as quatro decisões (§5.3) e as estratégias do `FLUXO_LOGICO_IMPLEMENTACAO.md` para a entrevista.
- Prioridade se atrasar: não cortar consulta, conferência, relatório nem regras no README. Cortar antes: testes extensos → paginação → vídeo vira prints → Docker vira "rodar local".
