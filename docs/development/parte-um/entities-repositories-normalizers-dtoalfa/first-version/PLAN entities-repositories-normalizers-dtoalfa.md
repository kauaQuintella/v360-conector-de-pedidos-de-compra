## CONTEXTO

Estava conversando sobre a estrutura do DESING.md em outro chat e perguntei a respeito do que deveria ser realizado no primeiro dia que estava escrito. Ele me detalhou um prompt e decidi migrar para um outro chat, onde eu poderia perguntar melhor a respeito do output que foi gerado, sem impactar na conversa sobre arquitetura que estava tendo no outro chat.

## PROMPT ENVIADO

Estou com este projeto para realizar e preciso que

1 - analise DESING.md e entenda o contexto da aplicacão.
2 - Compare esse plano de implementacão com base no que foi analisado com o md que foi lido.
3 - Diga sua opinião, formalize o que deve ser feito e me pergunte se deve realizar a implementacão do que foi dito.

Segue o plano de implementacão:

    O que resta hoje (Steps 2–10)
    STEP 2 — Criar estrutura de pacotes (5 min)
    Criar os pacotes dentro de com.v360.prosel.conectorpedidoscompra:

    controller/
    dto/
    alfa/
    entity/
    enums/
    ingestor/
    strategy/
    normalizer/
    repository/
    service/
    Na prática: criar os diretórios correspondentes em src/main/java/com/v360/prosel/conectorpedidoscompra/.

    STEP 3 — Entidades JPA (30 min)
    Arquivos a criar:

    enums/StatusPedido.java
    entity/Fornecedor.java
    entity/Pedido.java
    entity/Item.java
    enums/StatusPedido.java
    java
    public enum StatusPedido {
        OPEN, CLOSED, BLOCKED
    }
    entity/Fornecedor.java
    Pontos-chave:

    @Id @GeneratedValue com UUID
    cnpj com @Column(unique = true, nullable = false) — CNPJ único global (seção 3.2)
    nome como String
    entity/Pedido.java
    Pontos-chave:

    @Id @GeneratedValue com UUID
    @ManyToOne para Fornecedor
    numeroPedidoOrigem e clienteOrigem com @Table(uniqueConstraints = ...) na classe
    status como @Enumerated(EnumType.STRING) com tipo StatusPedido
    dataCriacao como Instant
    dataIngestao como Instant — setar no service antes de salvar (ou @CreationTimestamp)
    moeda como String (3 chars)
    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true) para itens
    entity/Item.java
    Pontos-chave:

    @Id @GeneratedValue com UUID
    @ManyToOne para Pedido
    linha, codigoMaterial, descricao, unidadeMedida como String
    quantidadePedida, quantidadeRecebida, precoUnitario como BigDecimal
    quantidade_pendente — coluna gerada (DECISÃO FECHADA):
    java
    @Column(name = "quantidade_pendente", insertable = false, updatable = false)
    private BigDecimal quantidadePendente;
    ⚠️ insertable = false, updatable = false é obrigatório. O Postgres calcula esse campo; se o JPA tentar gravar, o banco rejeita com erro porque a coluna é GENERATED ALWAYS.

    @Table(uniqueConstraints = @UniqueConstraint(columnNames = {"id_pedido", "linha"})) na classe
    Sobre ddl-auto=create-drop: com GENERATED ALWAYS AS ... STORED o Hibernate não sabe criar esse tipo de coluna automaticamente — ele cria a coluna como coluna normal, sem a cláusula de geração. Isso significa que o schema SQL do DESING (seção 3) precisa ser executado manualmente uma vez, ou via migration. Para hoje, a solução mais simples: mudar ddl-auto para validate e criar as tabelas manualmente ou via script SQL na primeira vez que subir o banco. Alternativamente, criar um arquivo src/main/resources/schema.sql com o DDL completo e usar spring.sql.init.mode=always — o Spring executa esse script na inicialização.

    STEP 4 — Repositories (15 min)
    Arquivos a criar:

    repository/FornecedorRepository.java
    java
    public interface FornecedorRepository extends JpaRepository<Fornecedor, UUID> {
        Optional<Fornecedor> findByCnpj(String cnpj);
    }
    repository/PedidoRepository.java
    java
    public interface PedidoRepository extends JpaRepository<Pedido, UUID> {
        Optional<Pedido> findByNumeroPedidoOrigemAndClienteOrigem(
            String numeroPedidoOrigem, String clienteOrigem);
    }
    repository/ItemRepository.java
    java
    public interface ItemRepository extends JpaRepository<Item, UUID> {
        Optional<Item> findByPedidoAndLinha(Pedido pedido, String linha);
        List<Item> findByPedido(Pedido pedido);
    }
    STEP 5 — Normalizadores (20 min)
    Arquivos a criar:

    normalizer/CnpjSanitizer.java
    Classe utilitária pura (sem estado, sem @Component):

    java
    public class CnpjSanitizer {
        public static String sanitize(String cnpj) {
            if (cnpj == null) return null;
            return cnpj.replaceAll("[^0-9]", "");
        }
    }
    normalizer/StatusMapper.java (interface)
    java
    public interface StatusMapper {
        StatusPedido map(String rawStatus);
    }
    normalizer/AlfaStatusMapper.java
    java
    @Component
    public class AlfaStatusMapper implements StatusMapper {
        @Override
        public StatusPedido map(String rawStatus) {
            return switch (rawStatus.toUpperCase()) {
                case "OPEN"    -> StatusPedido.OPEN;
                case "CLOSED"  -> StatusPedido.CLOSED;
                case "BLOCKED" -> StatusPedido.BLOCKED;
                default -> throw new IllegalArgumentException("Status inválido: " + rawStatus);
            };
        }
    }
    STEP 6 — DTOs de entrada do Alfa (15 min)
    Arquivos a criar:

    dto/alfa/AlfaFornecedorDTO.java
    java
    public record AlfaFornecedorDTO(
        @NotBlank String cnpj,
        @NotBlank String nome
    ) {}
    dto/alfa/AlfaItemDTO.java
    java
    public record AlfaItemDTO(
        @NotBlank String linha,
        @JsonProperty("codigo_material") @NotBlank String codigoMaterial,
        String descricao,
        @JsonProperty("unidade_medida") String unidadeMedida,
        @JsonProperty("quantidade_pedida")  @NotNull BigDecimal quantidadePedida,
        @JsonProperty("quantidade_recebida") @NotNull BigDecimal quantidadeRecebida,
        @JsonProperty("preco_unitario") @NotNull BigDecimal precoUnitario
    ) {}
    dto/alfa/AlfaPedidoDTO.java
    java
    public record AlfaPedidoDTO(
        @JsonProperty("numero_pedido") @NotBlank String numeroPedido,
        @JsonProperty("data_criacao") @NotNull Instant dataCriacao,
        @NotBlank String status,
        @NotBlank String moeda,
        @NotNull AlfaFornecedorDTO fornecedor,
        @NotEmpty List<AlfaItemDTO> itens
    ) {}
    Nota: o JSON do Alfa já vem em ISO 8601 ("2026-08-15T00:00:00Z") — Jackson desserializa Instant diretamente. Nenhuma conversão de data necessária para o Alfa (seção 6 do DESING).


## OUTPUT


Viewed DESING.md:1-310
Listed directory conectorpedidoscompra
Listed directory src
Viewed pom.xml:1-141
Viewed application.properties:1-18
Listed directory conectorpedidoscompra

Análise completa. Aqui está minha opinião formal:

---

## 📋 Análise: Plano de Implementação vs. DESING.md

### ✅ O que o plano acerta (total alinhamento)

| Ponto | DESING.md | Plano |
|---|---|---|
| `quantidade_pendente` com `insertable=false, updatable=false` | Seção 3.4 | ✅ Idêntico |
| `Fornecedor.cnpj` com `UNIQUE` global | Seção 3.2 | ✅ Correto |
| `ddl-auto=validate` + `schema.sql` | Seção 3.4 | ✅ Já configurado no `application.properties` |
| `CnpjSanitizer` sem `@Component` (utilitário puro) | Seção 1.2 | ✅ Correto |
| `StatusMapper` como interface + `AlfaStatusMapper` | Seção 1.1 / 6 | ✅ Correto |
| Jackson desserializa `Instant` diretamente do Alfa | Seção 6 | ✅ Correto |
| Records para DTOs | Boas práticas Java 21 | ✅ Adequado |

---

### ⚠️ Gaps e observações

**1. Steps 2–6 cobertos — mas faltam Steps 7, 8, 9, 10 do Dia 1**

O DESING.md lista no **Dia 1** os seguintes itens que o plano **não cobre** nesse conjunto de steps:

- `interface PedidoIngestor` (Strategy — o contrato que une tudo)
- `AlfaIngestor` (implementação da Strategy)
- `POST /ingest/alfa` com upsert (`AlfaController`)
- `PedidoService` com lógica de upsert
- Teste unitário do parser Alfa

> Sem o `PedidoIngestor` + `AlfaIngestor` + `PedidoService`, as entidades, repositórios e DTOs ficam "suspensos no ar" — o endpoint não sobe.

**2. `schema.sql` ausente** (crítico para rodar)

O `application.properties` já tem `spring.sql.init.mode=always` e `ddl-auto=validate`. O Spring vai procurar `src/main/resources/schema.sql` na inicialização. **Sem esse arquivo, a aplicação não sobe.** O plano menciona isso como opção mas não o inclui como entregável explícito dos Steps 2–6.

**3. Entidades sem Lombok** (decisão a tomar)

O `pom.xml` tem Lombok. O plano não menciona se as entidades usarão `@Data`/`@Getter`/`@Setter`/`@Builder`. Para entidades JPA com Lombok, a recomendação é `@Getter @Setter @NoArgsConstructor` (evitar `@EqualsAndHashCode` com relacionamentos bidirecionais). Vale alinhar isso antes de criar os arquivos.

**4. `clienteOrigem` na entidade `Pedido`** — o plano não menciona esse campo, mas o DESING.md exige (é metade da chave única de upsert: `numero_pedido_origem + cliente_origem`).

---

### 📦 O que será feito (formalização completa dos Steps 2–6 + complementos)

Implementar o seguinte nesta sequência:

1. **`schema.sql`** — DDL completo (Fornecedor, Pedido, Item com `GENERATED ALWAYS`, constraints e índices do DESING.md seção 3)
2. **Estrutura de pacotes** — criar os diretórios `controller/`, `dto/alfa/`, `entity/`, `enums/`, `ingestor/strategy/`, `normalizer/`, `repository/`, `service/`
3. **Entidades JPA** com Lombok (`@Getter @Setter @NoArgsConstructor`) — `StatusPedido`, `Fornecedor`, `Pedido` (com `clienteOrigem`), `Item` (com `quantidadePendente` read-only)
4. **Repositories** — `FornecedorRepository`, `PedidoRepository`, `ItemRepository` exatamente como no plano
5. **Normalizadores** — `CnpjSanitizer` (static puro), `StatusMapper` (interface), `AlfaStatusMapper` (`@Component`)
6. **DTOs Alfa** — `AlfaFornecedorDTO`, `AlfaItemDTO`, `AlfaPedidoDTO` como records com Bean Validation
7. **`PedidoIngestor`** (interface Strategy — necessário para o Step 8 funcionar)
8. **`AlfaIngestor`** (traduz `AlfaPedidoDTO` → entidades, usando `CnpjSanitizer` + `AlfaStatusMapper`)
9. **`PedidoService`** (upsert por `numeroPedidoOrigem + clienteOrigem`)
10. **`AlfaController`** (`POST /ingest/alfa`)

> [!IMPORTANT]
> Os itens 7–10 **não estão no plano original** (Steps 2–6), mas são a razão de existir de tudo o que vem antes. Recomendo implementar tudo em sequência para fechar o **Dia 1 completo** e ter o endpoint funcionando ao final.

---

**Devo prosseguir com a implementação de tudo isso (schema.sql + Steps 2–6 + interface/ingestor/service/controller do Alfa)?**