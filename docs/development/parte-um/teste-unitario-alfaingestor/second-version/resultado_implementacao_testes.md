# Resultado da Implementação — Suíte de Testes Unitários

**Data de execução:** 2026-09-06
**Baseado em:** `docs/plano_implementacao_testes.md`

---

## Resultado da Execução (`mvn test`)

```
Tests run: 12  — AlfaIngestorTest      ✅ 0 failures, 0 errors
Tests run: 5   — CnpjSanitizerTest     ✅ 0 failures, 0 errors
Tests run: 7   — StringSanitizerTest   ✅ 0 failures, 0 errors
Tests run: 3   — AlfaDateParserTest    ✅ 0 failures, 0 errors
Tests run: 8   — AlfaStatusMapperTest  ✅ 0 failures, 0 errors
Tests run: 7   — PedidoServiceTest     ✅ 0 failures, 0 errors
─────────────────────────────────────────────────────────
Total: 42 testes unitários → TODOS PASSANDO

Tests run: 1   — ConectorpedidoscompraApplicationTests  ❌ 1 error (pré-existente)
```

> **Nota sobre o único erro:** `ConectorpedidoscompraApplicationTests.contextLoads` falha porque tenta subir o `ApplicationContext` completo com `@SpringBootTest`, o que requer conexão com o PostgreSQL (`jdbc:postgresql://localhost:${POSTGRES_PORT}/${POSTGRES_DB}`). Este erro é **pré-existente** e não está relacionado a nenhuma das alterações realizadas — ocorre em qualquer ambiente sem o banco rodando.

---

## Arquivos Criados e Alterados

| Arquivo | Ação | Testes |
|---|---|---|
| `ingestor/AlfaIngestorTest.java` | **Alterado** | 12 (10 existentes corrigidos + 2 novos) |
| `normalizer/CnpjSanitizerTest.java` | **Criado** | 5 |
| `normalizer/AlfaDateParserTest.java` | **Criado** | 3 |
| `normalizer/AlfaStatusMapperTest.java` | **Criado** | 8 |
| `normalizer/StringSanitizerTest.java` | **Criado** | 7 |
| `service/PedidoServiceTest.java` | **Criado** | 7 |

---

## Detalhamento por Arquivo

### `AlfaIngestorTest.java` — 12 testes

**Correções aplicadas (3 do plano):**

1. Constante `DATA_CRIACAO`: `Instant.parse(...)` → `LocalDate.of(2026, 8, 15)`
2. Assertion de data no teste `toEntity_pedidoValido_mapeiaCabecalhoComSucesso`:
   - De: `isEqualTo(DATA_CRIACAO)`
   - Para: `isEqualTo(AlfaDateParser.parse(DATA_CRIACAO))`
3. Assinaturas dos helpers `pedido(...)` e `item(...)` alinhadas com os records atuais

**Novos testes adicionados (2 da lacuna identificada no plano):**

| Método | O que valida |
|---|---|
| `toEntity_nomeComQuebraLinha_removeQuebraLinha` | Ingestor sanitiza `\n` no nome do fornecedor via `StringSanitizer` |
| `toEntity_descricaoComQuebraLinha_removeQuebraLinha` | Ingestor sanitiza `\n` na descrição do item via `StringSanitizer` |

---

### `CnpjSanitizerTest.java` — 5 testes

| Método | Cenário |
|---|---|
| `sanitize_cnpjComMascara_retornaSomenteDigitos` | `"12.345.678/0001-90"` → `"12345678000190"` |
| `sanitize_cnpjSemMascara_retornaMesmoValor` | String já limpa passa inalterada |
| `sanitize_cnpjNulo_retornaNull` | `null` retorna `null` sem exceção |
| `sanitize_cnpjComEspacos_removeEspacos` | Espaços são considerados caracteres não-numéricos |
| `sanitize_cnpjVazio_retornaStringVazia` | String vazia retorna vazia |

---

### `AlfaDateParserTest.java` — 3 testes

| Método | Cenário |
|---|---|
| `parse_dataValida_retornaInstantMeiaNoite` | `2026-08-05` → `2026-08-05T03:00:00Z` (UTC-3) |
| `parse_dataNula_retornaNull` | `null` retorna `null` sem exceção |
| `parse_dataValida_fusoCorretoAplicado` | Verifica explicitamente que o fuso `America/Sao_Paulo` é aplicado, não UTC fixo |

---

### `AlfaStatusMapperTest.java` — 8 testes

| Método | Cenário |
|---|---|
| `map_statusOpen_retornaEnumOpen` | `"OPEN"` → `StatusPedido.OPEN` |
| `map_statusClosed_retornaEnumClosed` | `"CLOSED"` → `StatusPedido.CLOSED` |
| `map_statusBlocked_retornaEnumBlocked` | `"BLOCKED"` → `StatusPedido.BLOCKED` |
| `map_statusLowercase_mapeiaInsensitivo` | `"open"` → `StatusPedido.OPEN` |
| `map_statusMixedCase_mapeiaInsensitivo` | `"Open"` → `StatusPedido.OPEN` |
| `map_statusComEspacos_mapeiaAposTrim` | `" OPEN "` → `StatusPedido.OPEN` |
| `map_statusDesconhecido_lancaIllegalArgumentException` | `"PENDENTE"` lança `IllegalArgumentException` com mensagem contendo `"PENDENTE"` |
| `map_statusNulo_lancaIllegalArgumentException` | `null` lança `IllegalArgumentException` |

---

### `StringSanitizerTest.java` — 7 testes

| Método | Cenário |
|---|---|
| `sanitize_stringComQuebraLinha_substituiPorEspaco` | `\n` → espaço |
| `sanitize_stringComRetornoCarro_substituiPorEspaco` | `\r` → espaço |
| `sanitize_stringComTabulacao_substituiPorEspaco` | `\t` → espaço |
| `sanitize_stringComMultiplosControles_substituiTodos` | `\n\r\t` cada um vira espaço |
| `sanitize_stringLimpa_retornaMesmoValor` | String sem controles passa inalterada |
| `sanitize_stringNula_retornaNull` | `null` retorna `null` sem exceção |
| `sanitize_stringComEspacosNasBordas_aplicaTrim` | `"  Empresa  "` → `"Empresa"` |

---

### `PedidoServiceTest.java` — 7 testes (com Mockito)

**Estratégia:** `@ExtendWith(MockitoExtension.class)` — sem Spring Boot, sem banco.

| Método | Caminho testado |
|---|---|
| `upsert_fornecedorNovo_salvaNoBanco` | `resolverFornecedor` → branch `save` (fornecedor ausente no repositório) |
| `upsert_fornecedorExistente_atualizaNome` | `resolverFornecedor` → branch `atualizarFornecedor` |
| `upsert_pedidoNovo_criaComDataIngestao` | `criarPedido` → verifica que `dataIngestao` é preenchida |
| `upsert_pedidoNovo_itensDevemSerSalvos` | **Teste de regressão do bug do `.clear()`** — garante que `itemRepository.save()` é chamado N vezes mesmo que o objeto `incoming` seja mutado internamente |
| `upsert_pedidoExistente_atualizaCamposCorretamente` | `atualizarPedido` → verifica `status`, `moeda`, `dataCriacao` e `dataIngestao` |
| `upsert_pedidoExistente_itemNovo_criaItem` | `resolverItens` → branch `criarItem` (item ausente no repositório) |
| `upsert_pedidoExistente_itemExistente_atualizaItem` | `resolverItens` → branch `atualizarItem` (item presente no repositório) |

---

## Observação sobre o teste de regressão

O teste `upsert_pedidoNovo_itensDevemSerSalvos` é o mais estratégico da suíte. Ele protege especificamente contra o bug documentado em `analise_bug_payload_alfa.md` onde o método `criarPedido` chamava `incoming.getItens().clear()` antes de `resolverItens` processar a lista. Se esse bug for reintroduzido, o teste falhará com:

```
Wanted 2 times but was 0 times:
→ itemRepository.save(any Item)
```
