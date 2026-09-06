# Plano de Implementação — Suíte de Testes Unitários

**Data:** 2026-09-06
**Baseado em:** `docs/analise_projeto_testes.md` + leitura direta dos arquivos de produção e do `AlfaIngestorTest.java` existente.

---

## O que foi pedido

Ler o documento `analise_projeto_testes.md`, validar se o que ele propõe é de fato necessário e se há lacunas, e gerar este plano de implementação focado **exclusivamente em testes** — sem tocar em código de produção.

---

## Validação Crítica da Análise Original

Antes do plano, é necessário corrigir e complementar alguns pontos do documento de análise.

### ✅ Ponto confirmado — `PedidoServiceTest` com Mockito

A análise pede testes para o `PedidoService` com Mockito. Isso é **necessário e correto**. O `PedidoService` contém a lógica de upsert (caminhos create/update para fornecedor, pedido e item) — sem testes, qualquer regressão nesse fluxo é silenciosa.

### ✅ Ponto confirmado — Testes de utilitários isolados

A análise pede testes para `CnpjSanitizer`, `AlfaDateParser` e `AlfaStatusMapper`. Isso é **necessário e correto**. São utilitários puros, sem dependências, que encapsulam regras críticas de transformação e merecem cobertura dedicada.

### ⚠️ Lacuna 1 — `StringSanitizer` não está no plano

O documento de análise **não menciona** testes para o `StringSanitizer`, que foi criado recentemente. Ele é um normalizador puro com lógica própria (`\n`, `\r`, `\t` → espaço + trim) e deve ter sua própria suíte, seguindo o mesmo padrão dos demais utilitários.

### ⚠️ Lacuna 2 — O `AlfaIngestorTest` precisa de mais do que os 3 ajustes listados

A análise lista 3 correções necessárias no `AlfaIngestorTest`. Ao ler o código real, identifiquei **2 problemas adicionais não mencionados**:

1. **`StringSanitizer` não é coberto pelo `AlfaIngestorTest` atual.** O ingestor agora sanitiza `nome` do fornecedor e `descricao` dos itens. O teste `toEntity_itemCompleto_mapeiaTodosOsCampos` (linha 154) verifica `"Produto de teste"` que já é uma string limpa — não há nenhum teste que valide o comportamento quando o nome/descrição contém `\n`. Dois cenários novos devem ser adicionados.

2. **O teste `toEntity_listaItensVazia_retornaPedidoSemItens` (linha 215) pode ser questionável** com `@NotEmpty` no DTO. Na prática, Bean Validation rejeitaria um DTO com lista vazia antes de chegar ao ingestor — o teste ainda tem valor como teste de unidade pura do ingestor, mas deve ter `@DisplayName` que deixe claro que é um teste de contrato interno, não de payload real.

### ✅ Ponto confirmado — Os 3 ajustes no `AlfaIngestorTest` são todos necessários

1. `DATA_CRIACAO` como `Instant` → deve ser `LocalDate` (**quebra de compilação atual**)
2. Assertions de data devem comparar com `AlfaDateParser.parse(DATA_CRIACAO)` 
3. Assinaturas dos helpers devem estar alinhadas com os records atuais

---

## Estrutura de Arquivos a Criar/Alterar

```
src/test/java/com/v360/prosel/conectorpedidoscompra/
├── ingestor/
│   └── AlfaIngestorTest.java          ← ALTERAR (existente, desatualizado)
├── normalizer/
│   ├── CnpjSanitizerTest.java         ← CRIAR (novo)
│   ├── AlfaDateParserTest.java        ← CRIAR (novo)
│   ├── AlfaStatusMapperTest.java      ← CRIAR (novo)
│   └── StringSanitizerTest.java       ← CRIAR (novo) — lacuna do documento original
└── service/
    └── PedidoServiceTest.java         ← CRIAR (novo)
```

---

## ETAPA 1 — Corrigir `AlfaIngestorTest.java` (arquivo existente)

### 1.1 — Correção de compilação: `Instant` → `LocalDate`

**Linha 15:** Remover `import java.time.Instant;`

**Linha 15:** Adicionar `import java.time.LocalDate;` (e adicionar import do `AlfaDateParser` para usar nas assertions)

```java
// Imports a adicionar:
import java.time.LocalDate;
import com.v360.prosel.conectorpedidoscompra.normalizer.AlfaDateParser;

// Constante — De:
private static final Instant DATA_CRIACAO = Instant.parse("2026-08-15T00:00:00Z");

// Para:
private static final LocalDate DATA_CRIACAO = LocalDate.of(2026, 8, 15);
```

### 1.2 — Correção das assertions de data

**Linha 83** — o teste `toEntity_pedidoValido_mapeiaCabecalhoComSucesso` compara:
```java
// De:
assertThat(resultado.getDataCriacao()).isEqualTo(DATA_CRIACAO);

// Para:
assertThat(resultado.getDataCriacao()).isEqualTo(AlfaDateParser.parse(DATA_CRIACAO));
```

### 1.3 — Revisão do helper `pedido(...)`

**Linha 65** — o helper passa `DATA_CRIACAO` diretamente ao construtor. Com a mudança para `LocalDate`, a assinatura do record `AlfaPedidoDTO` já aceita `LocalDate` — nenhuma mudança adicional no helper é necessária além da mudança da constante.

### 1.4 — Adicionar 2 testes novos: sanitização de strings no ingestor

Estes testes cobrem comportamentos do `AlfaIngestor` que não existem na suíte atual:

**Teste novo 1 — sanitização de `\n` no nome do fornecedor:**
```java
@Test
@DisplayName("Sanitização de quebra de linha no nome do fornecedor")
void toEntity_nomeComQuebraLinha_removeQuebraLinha() {
    AlfaPedidoDTO dto = pedido("AL-011", "OPEN",
            fornecedor("12345678000190", "Metalúrgica São Jorge\nS.A."),
            List.of(itemPadrao()));

    Pedido resultado = alfaIngestor.toEntity(dto);

    assertThat(resultado.getFornecedor().getNome())
            .isEqualTo("Metalúrgica São Jorge S.A.");
}
```

**Teste novo 2 — sanitização de `\n` na descrição do item:**
```java
@Test
@DisplayName("Sanitização de quebra de linha na descrição do item")
void toEntity_descricaoComQuebraLinha_removeQuebraLinha() {
    AlfaItemDTO itemComQuebraLinha = item(
            "1", "MAT-001", "Produto\ncom quebra\nde linha", "UN",
            new BigDecimal("10.0000"), new BigDecimal("3.0000"), new BigDecimal("99.9900")
    );
    AlfaPedidoDTO dto = pedido("AL-012", "OPEN",
            fornecedor("12345678000190", "Fornecedor Teste"),
            List.of(itemComQuebraLinha));

    Pedido resultado = alfaIngestor.toEntity(dto);

    assertThat(resultado.getItens().getFirst().getDescricao())
            .isEqualTo("Produto com quebra de linha");
}
```

---

## ETAPA 2 — Criar `CnpjSanitizerTest.java`

**Caminho:** `src/test/java/com/v360/prosel/conectorpedidoscompra/normalizer/CnpjSanitizerTest.java`

Cenários a cobrir (baseados no comportamento real da classe):

| Método de teste | Entrada | Saída esperada |
|---|---|---|
| `sanitize_cnpjComMascara_retornaSomenteDigitos` | `"12.345.678/0001-90"` | `"12345678000190"` |
| `sanitize_cnpjSemMascara_retornaMesmoValor` | `"12345678000190"` | `"12345678000190"` |
| `sanitize_cnpjNulo_retornaNull` | `null` | `null` |
| `sanitize_cnpjComEspacos_removeEspacos` | `"123 456 780001 90"` | `"12345678000190"` |
| `sanitize_cnpjVazio_retornaStringVazia` | `""` | `""` |

---

## ETAPA 3 — Criar `AlfaDateParserTest.java`

**Caminho:** `src/test/java/com/v360/prosel/conectorpedidoscompra/normalizer/AlfaDateParserTest.java`

Cenários a cobrir:

| Método de teste | Entrada | Saída esperada |
|---|---|---|
| `parse_dataValida_retornaInstantMeiaNoite` | `LocalDate.of(2026, 8, 5)` | `Instant.parse("2026-08-05T03:00:00Z")` (UTC, pois SP está UTC-3) |
| `parse_dataNula_retornaNull` | `null` | `null` |
| `parse_dataValida_fusoCorretoAplicado` | `LocalDate.of(2026, 1, 1)` (horário de verão) | verificar que o offset aplicado é `America/Sao_Paulo` e não UTC fixo |

> **Nota:** O segundo cenário do horário de verão é importante porque o fuso `America/Sao_Paulo` historicamente variava entre UTC-2 e UTC-3. O teste deve verificar que o `ZoneId` correto é aplicado, não um offset fixo.

---

## ETAPA 4 — Criar `AlfaStatusMapperTest.java`

**Caminho:** `src/test/java/com/v360/prosel/conectorpedidoscompra/normalizer/AlfaStatusMapperTest.java`

Cenários a cobrir (baseados no comportamento real da classe):

| Método de teste | Entrada | Saída esperada |
|---|---|---|
| `map_statusOpen_retornaEnumOpen` | `"OPEN"` | `StatusPedido.OPEN` |
| `map_statusClosed_retornaEnumClosed` | `"CLOSED"` | `StatusPedido.CLOSED` |
| `map_statusBlocked_retornaEnumBlocked` | `"BLOCKED"` | `StatusPedido.BLOCKED` |
| `map_statusLowercase_mapeiaInsensitivo` | `"open"` | `StatusPedido.OPEN` |
| `map_statusMixedCase_mapeiaInsensitivo` | `"Open"` | `StatusPedido.OPEN` |
| `map_statusComEspacos_mapeiaAposTrip` | `" OPEN "` | `StatusPedido.OPEN` |
| `map_statusDesconhecido_lancaIllegalArgumentException` | `"PENDENTE"` | `IllegalArgumentException` com mensagem contendo `"PENDENTE"` |
| `map_statusNulo_lancaIllegalArgumentException` | `null` | `IllegalArgumentException` |

---

## ETAPA 5 — Criar `StringSanitizerTest.java` *(lacuna do documento original)*

**Caminho:** `src/test/java/com/v360/prosel/conectorpedidoscompra/normalizer/StringSanitizerTest.java`

Cenários a cobrir:

| Método de teste | Entrada | Saída esperada |
|---|---|---|
| `sanitize_stringComQuebraLinha_substituiPorEspaco` | `"Empresa\nS.A."` | `"Empresa S.A."` |
| `sanitize_stringComRetornoCarro_substituiPorEspaco` | `"Empresa\rS.A."` | `"Empresa S.A."` |
| `sanitize_stringComTabulacao_substituiPorEspaco` | `"Empresa\tS.A."` | `"Empresa S.A."` |
| `sanitize_stringComMultiplosControles_substituiTodos` | `"Empresa\n\r\tS.A."` | `"Empresa   S.A."` → após trim e replace: `"Empresa S.A."` |
| `sanitize_stringLimpa_retornaMesmoValor` | `"Empresa Normal"` | `"Empresa Normal"` |
| `sanitize_stringNula_retornaNull` | `null` | `null` |
| `sanitize_stringComEspacosNasBordas_aplicaTrim` | `"  Empresa  "` | `"Empresa"` |

---

## ETAPA 6 — Criar `PedidoServiceTest.java`

**Caminho:** `src/test/java/com/v360/prosel/conectorpedidoscompra/service/PedidoServiceTest.java`

**Estratégia:** `@ExtendWith(MockitoExtension.class)` com `@Mock` nos três repositórios e `@InjectMocks` no `PedidoService`. Sem Spring Boot, sem banco.

### Cenários a cobrir

#### Fornecedor

| Método de teste | Cenário | Verificação |
|---|---|---|
| `upsert_fornecedorNovo_salvaNoBanco` | `findByCnpj` retorna `Optional.empty()` | `fornecedorRepository.save(incoming)` é chamado 1x |
| `upsert_fornecedorExistente_atualizaNome` | `findByCnpj` retorna fornecedor existente | `existente.setNome(...)` é chamado e `save` é chamado com o existente |

#### Pedido — caminho CREATE

| Método de teste | Cenário | Verificação |
|---|---|---|
| `upsert_pedidoNovo_criaComDataIngestao` | `findByNumeroPedidoOrigemAndClienteOrigem` retorna `Optional.empty()` | `dataIngestao` não é nula no objeto salvo |
| `upsert_pedidoNovo_itensDevemSerSalvos` | pedido novo com 2 itens | `itemRepository.save(...)` é chamado **2 vezes** — este é o teste de regressão do bug do `.clear()` |
| `upsert_pedidoNovo_listaItensClearNaoAfetaResolverItens` | mesmo do anterior | verificar que a lista local copiada ainda tem os itens após `resolverPedido` |

#### Pedido — caminho UPDATE

| Método de teste | Cenário | Verificação |
|---|---|---|
| `upsert_pedidoExistente_atualizaCamposCorretamente` | `findByNumeroPedidoOrigemAndClienteOrigem` retorna pedido existente | `status`, `dataCriacao`, `moeda` do existente são atualizados com valores do incoming |
| `upsert_pedidoExistente_itensExistentesDevemSerAtualizados` | pedido existente com item que já existe | `atualizarItem` é invocado (verificar via `itemRepository.save(existente)`) |
| `upsert_pedidoExistente_itensNovosDevemSerCriados` | pedido existente com item novo | `criarItem` é invocado (verificar via `itemRepository.save(incoming)`) |

---

## Resumo dos Arquivos

| Arquivo | Ação | Etapa |
|---|---|---|
| `ingestor/AlfaIngestorTest.java` | **Alterar** — 3 correções + 2 testes novos de sanitização | 1 |
| `normalizer/CnpjSanitizerTest.java` | **Criar** — 5 cenários | 2 |
| `normalizer/AlfaDateParserTest.java` | **Criar** — 3 cenários | 3 |
| `normalizer/AlfaStatusMapperTest.java` | **Criar** — 8 cenários | 4 |
| `normalizer/StringSanitizerTest.java` | **Criar** — 7 cenários *(lacuna do doc original)* | 5 |
| `service/PedidoServiceTest.java` | **Criar** — 8 cenários com Mockito | 6 |

**Nenhum arquivo de produção será alterado.**
