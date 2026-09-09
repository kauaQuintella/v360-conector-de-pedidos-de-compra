# Análise Estrutural do Projeto — V360 Conector de Pedidos de Compra

> **Data da análise:** 06/09/2026  
> **Escopo:** Análise completa do estado atual do projeto, comparação com o documento de arquitetura `DESING.md`, e avaliação de conformidade estrutural.  
> **Método:** Leitura exaustiva de todos os arquivos de código-fonte, testes, configuração, infraestrutura, payloads de exemplo e documentação do projeto.

---

## Índice

1. [Estrutura Real do Projeto](#1-estrutura-real-do-projeto)
2. [Mapa de Pacotes e Classes Implementados](#2-mapa-de-pacotes-e-classes-implementados)
3. [Comparação: Design Planejado vs. Implementação Real](#3-comparação-design-planejado-vs-implementação-real)
   - 3.1 [Arquitetura em Camadas](#31-arquitetura-em-camadas)
   - 3.2 [Padrões de Projeto](#32-padrões-de-projeto-strategy--factory)
   - 3.3 [Modelo de Dados e Schema SQL](#33-modelo-de-dados-e-schema-sql)
   - 3.4 [Normalizadores Compartilhados](#34-normalizadores-compartilhados)
   - 3.5 [Persistência e Upsert](#35-estratégia-de-persistência-e-upsert)
   - 3.6 [Conferência e Relatórios](#36-conferência-e-relatórios)
   - 3.7 [Particularidades por Cliente](#37-particularidades-por-cliente-ingestão)
4. [Fluxo de Execução Real (End-to-End)](#4-fluxo-de-execução-real-end-to-end)
5. [Cobertura de Testes](#5-cobertura-de-testes)
6. [Infraestrutura e DevOps](#6-infraestrutura-e-devops)
7. [Estado do Cronograma (Kanban)](#7-estado-do-cronograma-kanban)
8. [Decisões em Aberto](#8-decisões-em-aberto-herdadas-do-desingmd)
9. [Resumo de Conformidade](#9-resumo-de-conformidade)
10. [Conclusão Geral](#10-conclusão-geral)

---

## 1. Estrutura Real do Projeto

```
conectorpedidoscompra/
├── .env                          # Variáveis de ambiente (portas, credenciais DB)
├── .gitignore
├── AI_USAGE.md                   # Registro de uso de IA no desenvolvimento
├── Dockerfile                    # Build multi-stage (Maven + JRE Alpine)
├── README.md                     # Documentação do projeto
├── compose.yaml                  # Docker Compose (Postgres + App + pgAdmin)
├── pom.xml                       # Maven (Spring Boot 4.1.1, Java 21)
│
├── public/                       # Payloads de exemplo dos clientes
│   ├── alfaEnergiaPayload.json
│   ├── betaAlimentos/
│   │   ├── cabecalho.csv
│   │   └── itens.csv
│   └── gamaLogisticaPayload.json
│
├── docs/                         # Documentação do projeto
│   ├── desing/
│   │   └── DESING.md             # Documento mestre de arquitetura
│   ├── tasks/
│   │   ├── DIA1.md ... DIA7.md   # Cronograma diário (Kanban)
│   └── development/
│       └── parte-um/
│           ├── entities-repositories-normalizers-dtoalfa/
│           │   ├── first-version/    # Plano e auditoria iniciais
│           │   └── second-version/   # Correções pós-teste real
│           └── teste-unitario-alfaingestor/
│               ├── first-version/    # Plano, implementação e output de testes
│               └── second-version/   # Correção pós-bugs de payload
│
└── src/
    ├── main/
    │   ├── java/com/v360/prosel/conectorpedidoscompra/
    │   │   ├── ConectorpedidoscompraApplication.java
    │   │   ├── controller/
    │   │   │   └── AlfaController.java
    │   │   ├── dto/
    │   │   │   └── alfa/
    │   │   │       ├── AlfaPayloadDTO.java
    │   │   │       ├── AlfaPedidoDTO.java
    │   │   │       ├── AlfaFornecedorDTO.java
    │   │   │       └── AlfaItemDTO.java
    │   │   ├── entity/
    │   │   │   ├── Fornecedor.java
    │   │   │   ├── Pedido.java
    │   │   │   └── Item.java
    │   │   ├── enums/
    │   │   │   └── StatusPedido.java
    │   │   ├── ingestor/
    │   │   │   ├── AlfaIngestor.java
    │   │   │   └── strategy/
    │   │   │       └── PedidoIngestor.java
    │   │   ├── normalizer/
    │   │   │   ├── AlfaDateParser.java
    │   │   │   ├── AlfaStatusMapper.java
    │   │   │   ├── CnpjSanitizer.java
    │   │   │   ├── StatusMapper.java
    │   │   │   └── StringSanitizer.java
    │   │   ├── repository/
    │   │   │   ├── FornecedorRepository.java
    │   │   │   ├── ItemRepository.java
    │   │   │   └── PedidoRepository.java
    │   │   └── service/
    │   │       └── PedidoService.java
    │   └── resources/
    │       ├── application.properties
    │       └── schema.sql
    └── test/
        └── java/com/v360/prosel/conectorpedidoscompra/
            ├── ingestor/
            │   └── AlfaIngestorTest.java
            ├── normalizer/
            │   ├── AlfaDateParserTest.java
            │   ├── AlfaStatusMapperTest.java
            │   ├── CnpjSanitizerTest.java
            │   └── StringSanitizerTest.java
            └── service/
                └── PedidoServiceTest.java
```

---

## 2. Mapa de Pacotes e Classes Implementados

| Pacote | Classe | Tipo | Propósito |
|:---|:---|:---|:---|
| `controller` | `AlfaController` | `@RestController` | Endpoint `POST /ingest/alfa` — recebe payload, valida, delega ao ingestor e persiste via service |
| `dto.alfa` | `AlfaPayloadDTO` | `record` | Envelope raiz (`purchase_orders: [...]`) — wrapper de lote |
| `dto.alfa` | `AlfaPedidoDTO` | `record` | Pedido individual do Alfa (`po_number`, `created_at`, `status`, etc.) |
| `dto.alfa` | `AlfaFornecedorDTO` | `record` | Fornecedor aninhado (`tax_id`, `name`) |
| `dto.alfa` | `AlfaItemDTO` | `record` | Item de compra (`line`, `material`, `uom`, `quantity_ordered`, etc.) |
| `entity` | `Fornecedor` | `@Entity` | Fornecedor único por CNPJ no ecossistema V360 |
| `entity` | `Pedido` | `@Entity` | Pedido normalizado, com enum de status e lista de itens |
| `entity` | `Item` | `@Entity` | Linha de item com `quantidade_pendente` calculada pelo banco |
| `enums` | `StatusPedido` | `enum` | `OPEN`, `CLOSED`, `BLOCKED` |
| `ingestor` | `AlfaIngestor` | `@Component` | Strategy para tradução DTO Alfa → entidades de domínio |
| `ingestor.strategy` | `PedidoIngestor<T>` | `interface` | Contrato genérico do padrão Strategy |
| `normalizer` | `AlfaDateParser` | Utilitário estático | `LocalDate` → `Instant` (fuso `America/Sao_Paulo`) |
| `normalizer` | `AlfaStatusMapper` | `@Component` | Traduz `"open"/"closed"/"blocked"` → `StatusPedido` |
| `normalizer` | `StatusMapper` | `interface` | Contrato genérico para mappers de status por cliente |
| `normalizer` | `CnpjSanitizer` | Utilitário estático | Remove máscara do CNPJ (somente dígitos) |
| `normalizer` | `StringSanitizer` | Utilitário estático | Remove `\n`, `\r`, `\t` de strings |
| `repository` | `FornecedorRepository` | `JpaRepository` | `findByCnpj(String)` |
| `repository` | `PedidoRepository` | `JpaRepository` | `findByNumeroPedidoOrigemAndClienteOrigem(...)` |
| `repository` | `ItemRepository` | `JpaRepository` | `findByPedidoAndLinha(...)`, `findByPedido(...)` |
| `service` | `PedidoService` | `@Service` | Upsert transacional idempotente de Fornecedor + Pedido + Itens |

---

## 3. Comparação: Design Planejado vs. Implementação Real

### 3.1 Arquitetura em Camadas

O `DESING.md §1.2` define 7 camadas com responsabilidades bem delimitadas:

| Camada (DESING.md) | O que deveria fazer | O que deveria **NÃO** fazer | Estado no código |
|:---|:---|:---|:---|
| **Controller** | Validação de payload (Bean Validation), roteamento pro service | Lógica de negócio | ✅ **Conforme** — `AlfaController` usa `@Valid` e delega para `AlfaIngestor` + `PedidoService` |
| **DTO de entrada (por cliente)** | Espelhar o formato bruto de cada cliente | Normalização | ✅ **Conforme** — DTOs records com `@JsonProperty` espelhando o payload real |
| **Ingestor (Strategy)** | Traduzir DTO bruto → entidade de domínio, usando normalizadores | Persistência direta | ✅ **Conforme** — `AlfaIngestor` retorna `Pedido` transiente, sem tocar em repositórios |
| **Normalizadores compartilhados** | CNPJ, status, data, conversão de unidade | Lógica específica de um cliente só | ⚠️ **Parcialmente conforme** — ver seção 3.4 |
| **Service** | Upsert, regras de conferência | Acesso SQL direto | ✅ **Conforme** — `PedidoService` usa repositories, não SQL nativo |
| **Repository (Spring Data JPA)** | Acesso a dados | Lógica de negócio | ✅ **Conforme** — interfaces puras sem lógica |
| **Entidade / Postgres** | Persistência | — | ✅ **Conforme** |

> **💡 Nota:** A separação de responsabilidades está limpa: o Controller não tem lógica de negócio, o Ingestor não persiste, o Service não faz parse, e os Repositories são interfaces puras. Isso facilita tanto os testes unitários quanto a adição de novos clientes.

---

### 3.2 Padrões de Projeto (Strategy / Factory)

**O que o `DESING.md §1.3` define:**
- **Strategy**: cada cliente tem um `Ingestor` implementando `PedidoIngestor`.
- **Factory**: dispensável porque as rotas são dedicadas por cliente (`/ingest/alfa`, `/ingest/beta`, `/ingest/gama`) — o Spring resolve via injeção de dependência.

**Estado real:**

| Aspecto | Planejado | Implementado | Veredicto |
|:---|:---|:---|:---|
| Interface `PedidoIngestor<T>` | ✅ | ✅ `Pedido toEntity(T dto)` | ✅ Conforme |
| `AlfaIngestor` implementa `PedidoIngestor<AlfaPedidoDTO>` | ✅ | ✅ | ✅ Conforme |
| `BetaIngestor` implementa `PedidoIngestor<BetaPedidoDTO>` | ✅ | ❌ Não existe | ⏳ Pendente (DIA2) |
| `GamaIngestor` implementa `PedidoIngestor<GamaPedidoDTO>` | ✅ | ❌ Não existe | ⏳ Pendente (DIA5) |
| Rota dedicada `/ingest/alfa` | ✅ | ✅ | ✅ Conforme |
| Rota dedicada `/ingest/beta` | ✅ | ❌ Não existe | ⏳ Pendente (DIA2) |
| Rota dedicada `/ingest/gama` | ✅ | ❌ Não existe | ⏳ Pendente (DIA5) |
| Factory formal dispensado | ✅ Decisão consciente | ✅ Não implementado, conforme planejado | ✅ Conforme |

> **⚠️ Importante:** O padrão Strategy está corretamente implementado para o Alfa. A extensibilidade para Beta e Gama está garantida pela interface genérica `PedidoIngestor<T>`, que permite adicionar novos clientes sem alterar código existente (Open/Closed Principle). Os ingestores Beta e Gama são itens pendentes do cronograma, não falhas arquiteturais.

---

### 3.3 Modelo de Dados e Schema SQL

#### 3.3.1 Contrato JSON Unificado (§2)

O `DESING.md §2` define o contrato de saída normalizado. A entidade `Pedido` com seus relacionamentos reflete este contrato:

| Campo do contrato | Entidade JPA correspondente | Veredicto |
|:---|:---|:---|
| `id_pedido` (UUID) | `Pedido.id` (`@GeneratedValue UUID`) | ✅ |
| `numero_pedido` | `Pedido.numeroPedidoOrigem` | ✅ |
| `cliente_origem` | `Pedido.clienteOrigem` | ✅ |
| `data_criacao` (ISO) | `Pedido.dataCriacao` (`Instant`) | ✅ |
| `status` (enum) | `Pedido.status` (`StatusPedido`, `@Enumerated STRING`) | ✅ |
| `moeda` | `Pedido.moeda` (`VARCHAR(3)`) | ✅ |
| `fornecedor.id_fornecedor` | `Fornecedor.id` (UUID) | ✅ |
| `fornecedor.cnpj` | `Fornecedor.cnpj` (`UNIQUE NOT NULL`) | ✅ |
| `fornecedor.nome` | `Fornecedor.nome` | ✅ |
| `itens[].id_item` | `Item.id` (UUID) | ✅ |
| `itens[].linha` | `Item.linha` | ✅ |
| `itens[].codigo_material` | `Item.codigoMaterial` | ✅ |
| `itens[].descricao` | `Item.descricao` (`TEXT`) | ✅ |
| `itens[].unidade_medida` | `Item.unidadeMedida` | ✅ |
| `itens[].quantidade_pedida` | `Item.quantidadePedida` (`NUMERIC(15,4)` / `BigDecimal`) | ✅ |
| `itens[].quantidade_recebida` | `Item.quantidadeRecebida` (`NUMERIC(15,4)` / `BigDecimal`) | ✅ |
| `itens[].quantidade_pendente` | `Item.quantidadePendente` (`GENERATED ALWAYS AS ... STORED`) | ✅ |
| `itens[].preco_unitario` | `Item.precoUnitario` (`NUMERIC(15,4)` / `BigDecimal`) | ✅ |

#### 3.3.2 Schema Relacional (§3)

Comparação entre o DDL do `DESING.md §3` e o `schema.sql` real:

| Tabela | Planejada (DESING.md) | Presente no schema.sql | Entidade JPA | Veredicto |
|:---|:---|:---|:---|:---|
| `Fornecedor` | ✅ | ✅ | ✅ `Fornecedor.java` | ✅ Conforme |
| `Pedido` | ✅ | ✅ | ✅ `Pedido.java` | ✅ Conforme |
| `Item` | ✅ | ✅ | ✅ `Item.java` | ✅ Conforme |
| `Conferencia` | ✅ | ✅ | ❌ **Sem entidade JPA** | ⏳ Pendente (DIA4) |
| `Divergencia` | ✅ | ✅ | ❌ **Sem entidade JPA** | ⏳ Pendente (DIA4) |
| `uk_pedido_origem` | ✅ | ✅ | ✅ `@UniqueConstraint` | ✅ Conforme |
| `uk_item_pedido` | ✅ | ✅ | ✅ `@UniqueConstraint` | ✅ Conforme |
| Índices de performance | ✅ (3 índices) | ✅ (3 índices) | N/A (gerenciado pelo DDL) | ✅ Conforme |

> **ℹ️ Nota:** As tabelas `Conferencia` e `Divergencia` já existem no `schema.sql` (criadas na subida da aplicação), mas ainda não possuem entidades JPA mapeadas. Isso é esperado e coerente com o cronograma: elas serão implementadas no DIA4 junto com o módulo de conferência.

#### 3.3.3 Decisões Críticas de Schema

| Decisão (DESING.md) | Implementada? | Detalhes |
|:---|:---|:---|
| **§3.1** `Conferencia`/`Divergencia` para histórico de relatório | ✅ No DDL | Tabelas criadas; entidades JPA pendentes |
| **§3.2** Fornecedor único entre clientes (`cnpj UNIQUE` global) | ✅ | `@Column(unique = true)` + lógica de upsert em `PedidoService` |
| **§3.3** `NUMERIC(15,4)` em campos financeiros/quantidades | ✅ | `BigDecimal` com `precision=15, scale=4` nas entidades |
| **§3.4** `quantidade_pendente` como coluna gerada pelo banco | ✅ | `GENERATED ALWAYS AS ... STORED` + `insertable=false, updatable=false` no JPA |
| **§3.5** Upsert por `(numero_pedido_origem, cliente_origem)` via JPA | ✅ | `findByNumeroPedidoOrigemAndClienteOrigem` + save |
| **§3.5 [EM ABERTO]** Itens órfãos em reingestão | ❌ | Não decidido nem implementado |

---

### 3.4 Normalizadores Compartilhados

O `DESING.md §1.1` prevê 4 normalizadores compartilhados:

| Normalizador planejado | Implementado? | Classe real | Observações |
|:---|:---|:---|:---|
| `CnpjSanitizer` | ✅ | `CnpjSanitizer` | Utilitário estático puro, compartilhável entre todos os clientes |
| `StatusMapper` | ✅ | `StatusMapper` (interface) + `AlfaStatusMapper` | Interface genérica + implementação específica Alfa |
| `DateParser` | ⚠️ Parcial | `AlfaDateParser` | Implementado apenas para o Alfa (`LocalDate` → `Instant`). Falta interface genérica `DateParser` como existe para `StatusMapper` |
| `UnitConverter` | ❌ | Não existe | Pendente (DIA5 — necessário para conversão de unidade do Gama) |

**Normalizadores extras (não previstos no DESING.md, mas adicionados):**

| Classe | Propósito | Observação |
|:---|:---|:---|
| `StringSanitizer` | Remove `\n`, `\r`, `\t` de strings | Surgiu da correção de bug real com `\n` no nome do fornecedor. Adição justificada e benéfica |

> **⚠️ Ponto de atenção arquitetural:** O `AlfaDateParser` é um utilitário específico do Alfa, mas está no pacote `normalizer` (compartilhado). Diferente do `StatusMapper`, que possui uma interface genérica, o `DateParser` não tem essa abstração. Quando os ingestores Beta (data `dd/MM/yyyy`) e Gama (epoch seconds) forem implementados, será necessário decidir se cria-se uma interface `DateParser` genérica ou se cada cliente mantém seu parser isolado. A decisão é aceitável por agora, dado que cada formato de data é radicalmente diferente (ISO, BR, epoch), mas vale formalizar a justificativa.

---

### 3.5 Estratégia de Persistência e Upsert

| Decisão (DESING.md §4) | Implementada? | Como está no código |
|:---|:---|:---|
| **§4.1** Postgres desde o dia zero | ✅ | `application.properties` aponta para Postgres; `compose.yaml` sobe o container |
| **§4.2** Upsert via JPA (find + save), não SQL nativo | ✅ | `PedidoService.upsert()` faz `findBy...` + condicionais + `save()` |
| **§4.3** H2 só para testes unitários com mock | ✅ | Testes unitários usam `new` e Mockito; H2 no `pom.xml` mas sem uso em testes de integração |
| `ddl-auto=validate` (Hibernate não altera schema) | ✅ | Confirmado em `application.properties` |
| `schema.sql` executado na subida (`sql.init.mode=always`) | ✅ | Confirmado em `application.properties` |

**Lógica real do upsert em `PedidoService`:**

```
upsert(incoming) {
  1. Cópia defensiva dos itens (correção do bug .clear())
  2. resolverFornecedor: findByCnpj → se existe: atualiza nome; se não: cria novo
  3. resolverPedido: findByNumeroPedidoOrigemAndClienteOrigem →
     se existe: atualiza (status, dataCriacao, moeda, dataIngestao)
     se não: limpa itens, seta dataIngestao, salva
  4. resolverItens: para cada item, findByPedidoAndLinha →
     se existe: atualiza campos
     se não: vincula ao pedido e salva
}
```

> **💡 Dica:** A cópia defensiva dos itens antes do `.clear()` é uma correção crítica documentada em `analise_bug_payload_alfa.md` e coberta por teste de regressão em `PedidoServiceTest`.

---

### 3.6 Conferência e Relatórios

| Funcionalidade (DESING.md §5) | Estado |
|:---|:---|
| Casamento nota ↔ pedido por `numero_pedido` + `codigo_material` | ❌ Não implementado (DIA4) |
| 7 tipos de divergência definidos | ❌ Não implementado (DIA4) |
| Tolerância de preço | ❌ **[EM ABERTO]** — decisão pendente (DIA3) |
| Tratamento de `BLOCKED`/`CLOSED` | ❌ **[EM ABERTO]** — decisão pendente (DIA3) |
| Múltiplas linhas com mesmo material | ❌ **[EM ABERTO]** — decisão pendente (DIA3) |
| `POST /notas-fiscais/conferir` | ❌ Não implementado (DIA4) |
| `GET /relatorios/conferencias` | ❌ Não implementado (DIA4) |

> **ℹ️ Nota:** Todo o módulo de conferência está planejado para os DIAs 3 e 4 do cronograma. As tabelas SQL já existem, mas as 3 decisões de negócio mais críticas ainda estão em aberto (§5.3 do DESING.md).

---

### 3.7 Particularidades por Cliente (Ingestão)

Conforme a tabela do `DESING.md §6`:

| Cliente | Formato | Conversões específicas | Estado |
|:---|:---|:---|:---|
| **Alfa** | JSON aninhado | ~~Data já em ISO~~ → Corrigido para `LocalDate` com `AlfaDateParser` | ✅ **Implementado e testado** |
| **Beta** | CSV (2 arquivos, `;`, padrão BR) | Número BR, data `dd/MM/yyyy`, CNPJ mascarado, status pt-BR, join por `NUMERO_PEDIDO` | ❌ Pendente (DIA2) |
| **Gama** | JSON achatado (1 linha por item) | Agrupar por `ped`, epoch → `Instant`, centavos ÷ 100, status numérico, `fator_conv` | ❌ Pendente (DIA5) |

> **⚠️ Importante — Correção documentada no DESING.md §6 vs. realidade do Alfa:** O design original afirmava "Data já em ISO, nenhuma conversão de data necessária". Na realidade, o payload do Alfa envia a data como `"2026-08-05"` (apenas `YYYY-MM-DD`, sem horário/timezone), o que é um `LocalDate`, não um `Instant` ISO completo. Isso gerou o bug documentado em `AI_USAGE.md` e resultou na criação do `AlfaDateParser`. **O `DESING.md §6` ainda descreve "nenhuma conversão de data necessária" para o Alfa, o que está desatualizado em relação ao código real.**

---

## 4. Fluxo de Execução Real (End-to-End)

```
Cliente HTTP (Postman / Sistema Externo)
        │
        │  POST /ingest/alfa
        │  Body: { "purchase_orders": [...] }
        ▼
┌─────────────────────────────────────┐
│  AlfaController                     │
│  @Valid AlfaPayloadDTO              │
│  Para cada AlfaPedidoDTO:           │
│    ├── alfaIngestor.toEntity(dto)   │
│    └── pedidoService.upsert(pedido) │
└──────────┬──────────────────────────┘
           │
           ▼
┌─────────────────────────────────────┐
│  AlfaIngestor                       │
│  implements PedidoIngestor<...>     │
│  Usa:                               │
│    ├── CnpjSanitizer.sanitize()    │
│    ├── StringSanitizer.sanitize()  │
│    ├── AlfaStatusMapper.map()      │
│    └── AlfaDateParser.parse()      │
│  Retorna: Pedido transiente        │
└──────────┬──────────────────────────┘
           │
           ▼
┌─────────────────────────────────────┐
│  PedidoService                      │
│  @Transactional                     │
│  1. resolverFornecedor (find/save) │
│  2. resolverPedido (find/save)     │
│  3. resolverItens (find/save)      │
└──────────┬──────────────────────────┘
           │
           ▼
┌─────────────────────────────────────┐
│  PostgreSQL                         │
│  Fornecedor │ Pedido │ Item         │
│  Conferencia │ Divergencia          │
└─────────────────────────────────────┘
```

O fluxo está **100% alinhado** com o diagrama do `DESING.md §1.1`, com a ressalva de que apenas o caminho do Alfa está implementado. Os caminhos Beta e Gama seguirão exatamente o mesmo padrão, cada um com seu Controller, Ingestor e DTOs dedicados.

---

## 5. Cobertura de Testes

### 5.1 Suíte Atual: 42 testes unitários (100% passando)

| Classe de Teste | # Testes | Camada Testada | Tipo |
|:---|:---|:---|:---|
| `AlfaIngestorTest` | 12 | Ingestor (tradução DTO → entidade) | Unitário puro (`new`) |
| `AlfaDateParserTest` | 3 | Normalizador (data) | Unitário puro |
| `AlfaStatusMapperTest` | 8 | Normalizador (status) | Unitário puro |
| `CnpjSanitizerTest` | 5 | Normalizador (CNPJ) | Unitário puro |
| `StringSanitizerTest` | 7 | Normalizador (strings) | Unitário puro |
| `PedidoServiceTest` | 6-7 | Service (upsert) | Unitário com Mockito |

### 5.2 O que ainda não tem cobertura de teste

| Componente | Motivo |
|:---|:---|
| `AlfaController` (integração HTTP) | Teste de integração depende de Postgres rodando |
| `BetaIngestor`, `GamaIngestor` | Ainda não implementados |
| Conferência e Relatórios | Ainda não implementados |
| Repositórios (teste de integração real com banco) | Decisão de não usar H2 para testes de integração (§4.3 do DESING.md) |

---

## 6. Infraestrutura e DevOps

| Componente | Detalhes | Conforme ao DESING.md? |
|:---|:---|:---|
| **Java** | 21 (LTS) | ✅ |
| **Spring Boot** | 4.1.1 | ✅ |
| **PostgreSQL** | 17 (via Docker) | ✅ (§4.1 — Postgres desde o dia zero) |
| **Dockerfile** | Multi-stage: Maven 3.9 + Temurin 21 → JRE Alpine | ✅ |
| **compose.yaml** | 3 serviços: `postgres`, `app`, `pgadmin` | ✅ |
| **OpenCSV** | 5.12.0 (já no `pom.xml`) | ✅ Preparado para DIA2 (Beta) |
| **Lombok** | Presente | ✅ (Redução de boilerplate nas entidades) |
| **H2** | No `pom.xml` (scope não definido como test) | ⚠️ Presente mas sem uso ativo; coerente com §4.3 |

---

## 7. Estado do Cronograma (Kanban)

| Dia | Escopo | Status | Observações |
|:---|:---|:---|:---|
| **DIA1** (04/09) | Setup + Ingestão Alfa | ✅ **100% Concluído** | 10/10 itens marcados; 2 ciclos de correção (payload + bugs) |
| **DIA2** (05/09) | Ingestão Beta (CSV) | ❌ **Pendente** | 0/7 itens; OpenCSV já no pom.xml |
| **DIA3** (06/09) | Consultas + Decisões de Conferência | ❌ **Pendente** | 0/6 itens; 3 decisões de negócio [EM ABERTO] |
| **DIA4** (07/09) | Conferência + Relatório + `git tag parte-1` | ❌ **Pendente** | 0/5 itens |
| **DIA5** (08/09) | Gama Logística (Parte 2) | ❌ **Pendente** | 0/8 itens |
| **DIA6** (09/09) | Entregáveis Finais (README, AI_USAGE, demo) | ❌ **Pendente** | 0/5 itens |
| **DIA7** (10/09) | Buffer / Revisão | ❌ **Pendente** | 0/2 itens |

---

## 8. Decisões em Aberto (Herdadas do DESING.md)

As seguintes decisões do `DESING.md` estão marcadas como **[EM ABERTO]** e ainda não foram resolvidas no código:

| # | Decisão | Seção | Impacto | Prazo sugerido |
|:---|:---|:---|:---|:---|
| 1 | **Tolerância de preço na conferência** (margem de arredondamento aceitável ao comparar `quantidade × preco_unitario` com o valor da nota) | §5.3 | Alto — define se a conferência reprova por centavos de diferença | DIA3 |
| 2 | **Pedido `BLOCKED`/`CLOSED` na conferência** (reprovação automática da nota inteira, ou é apenas mais um tipo de divergência?) | §5.3 | Alto — muda o fluxo de conferência | DIA3 |
| 3 | **Múltiplas linhas com mesmo material** (como casar a nota quando há linhas duplicadas de material no pedido?) | §5.3 | Médio — cenário de borda, mas exige regra clara | DIA3 |
| 4 | **Itens órfãos em reingestão** (pedido reenviado com item a menos — deletar, marcar inativo, ou ignorar?) | §3.5 | Médio — afeta integridade do upsert | Não agendado |

> **🚨 Atenção:** A decisão #4 (itens órfãos) **não aparece em nenhum DIA do cronograma**. Embora a entidade `Pedido` tenha `orphanRemoval = true` no mapeamento JPA, o `PedidoService` resolve itens individualmente (por linha), sem remover itens que não vieram na reingestão. Isso pode resultar em comportamento inconsistente: o `orphanRemoval` da cascata JPA poderia remover itens em certos cenários de flush, mas não há regra explícita definida. Recomenda-se fechar essa decisão antes do DIA4 (conferência), pois itens fantasma podem gerar divergências falsas.

---

## 9. Resumo de Conformidade

| Status | Contagem | Exemplos |
|:---|:---|:---|
| ✅ **Conforme** | 24 | Camadas, Strategy, Schema SQL, CNPJ/Status/String sanitizers, Upsert via JPA, Postgres desde dia zero, contrato de dados, tipagem NUMERIC, coluna gerada, constraints únicas, índices, Dockerfile, compose.yaml |
| ⚠️ **Parcialmente conforme** | 2 | `DateParser` sem interface genérica; `DESING.md §6` desatualizado sobre data do Alfa |
| ⏳ **Pendente (cronograma)** | 10 | BetaIngestor, GamaIngestor, UnitConverter, BetaController, GamaController, entidades Conferencia/Divergencia, endpoints de consulta/conferência/relatório |
| ❓ **Em aberto (decisão)** | 4 | Tolerância de preço, BLOCKED/CLOSED, material duplicado, itens órfãos |

---

## 10. Conclusão Geral

### O que está bem feito

1. **Separação de camadas impecável** — Controller → Ingestor (Strategy) → Normalizadores → Service → Repository → Banco. Nenhuma camada invade a responsabilidade de outra.
2. **Padrão Strategy implementado corretamente** — A interface genérica `PedidoIngestor<T>` permite adicionar Beta e Gama sem alterar código existente.
3. **Schema SQL robusto** — Coluna computada, constraints de unicidade, índices de performance, e tabelas de conferência já criadas no DDL.
4. **Testes unitários sólidos** — 42 testes puros (sem Spring Context) cobrindo ingestor, normalizadores e service com regressão para bugs reais.
5. **Processo de desenvolvimento maduro** — Cada correção foi precedida de análise documentada, plano de implementação e auditoria pós-execução (versionamento em `first-version/` e `second-version/`).
6. **Upsert idempotente com cópia defensiva** — Bug do `.clear()` corrigido e coberto por teste de regressão.

### O que requer atenção

1. **`DESING.md §6` desatualizado** — Ainda diz "nenhuma conversão de data necessária" para o Alfa, mas o código real usa `AlfaDateParser` para converter `LocalDate` → `Instant`.
2. **`DateParser` sem interface genérica** — Diferente do `StatusMapper`, não existe contrato `DateParser` reutilizável. Pode ser uma simplificação válida (cada formato é muito diferente), mas falta a justificativa formal.
3. **Decisão de itens órfãos não agendada** — A interação entre `orphanRemoval = true` e o upsert item-a-item pode gerar comportamento não determinístico.
4. **3 decisões de negócio críticas pendentes** — Tolerância de preço, tratamento de BLOCKED/CLOSED, e material duplicado precisam ser fechadas antes da implementação da conferência (DIA3–DIA4).

### Veredicto final

> O projeto está **em alta conformidade com o `DESING.md`** para tudo que já foi implementado (DIA1 — Ingestão Alfa). As divergências encontradas são menores (documentação desatualizada, falta de interface genérica para DateParser) e os itens pendentes são coerentes com o cronograma planejado. A arquitetura está limpa, extensível e bem testada.
