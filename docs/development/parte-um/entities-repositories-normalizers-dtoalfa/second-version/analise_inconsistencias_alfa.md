# Análise de Inconsistências: Integração Cliente Alfa

Esta análise compara o design documentado, a implementação atual dos DTOs/Controllers e o payload real fornecido pelo cliente Alfa (`alfaEnergiaPayload.json`).

## 1. Estrutura Raiz do Payload vs. Controller

- **Realidade do Payload:** O arquivo `alfaEnergiaPayload.json` possui um objeto raiz contendo uma lista de pedidos sob a chave `purchase_orders`. Ou seja, o payload pode conter múltiplos pedidos em uma única requisição.
- **Implementação Atual:** O `AlfaController` (`POST /ingest/alfa`) e o `AlfaPedidoDTO` estão desenhados para receber um único pedido diretamente na raiz do JSON (`@RequestBody @Valid AlfaPedidoDTO dto`).
- **Problema Crítico:** A API quebrará ao tentar desserializar o payload real, pois espera um objeto de pedido (com campos como `po_number`, `status`, etc.) e receberá um objeto contendo a propriedade `purchase_orders` (um array). A API precisa ser adaptada para receber um DTO wrapper (ex: `AlfaPayloadDTO` contendo `List<AlfaPedidoDTO>`) e o controller deve iterar sobre essa lista.

## 2. Mapeamento de Campos (DTO vs. JSON)

Os DTOs atuais em `src/main/java/com/v360/prosel/conectorpedidoscompra/dto/alfa/` assumem que muitos campos no JSON de entrada estarão em português ou com nomes específicos que não correspondem ao payload real (que está majoritariamente em inglês). Faltam anotações `@JsonProperty` em quase todos os campos para fazer o de/para correto.

### Inconsistências no `AlfaPedidoDTO`
| Campo DTO | Esperado pelo DTO (JSON property) | Real no Payload (`alfaEnergiaPayload.json`) | Status |
| :--- | :--- | :--- | :--- |
| `numeroPedido` | `po_number` | `po_number` | ✅ Correto |
| `dataCriacao` | `created_at` | `created_at` | ⚠️ Nome correto, mas formato incompatível (veja seção 3) |
| `status` | `status` | `status` | ✅ Correto |
| `moeda` | `moeda` (implícito) | `currency` | ❌ Incorreto (Falta `@JsonProperty("currency")`) |
| `fornecedor` | `fornecedor` (implícito) | `vendor` | ❌ Incorreto (Falta `@JsonProperty("vendor")`) |
| `itens` | `itens` (implícito) | `items` | ❌ Incorreto (Falta `@JsonProperty("items")`) |

### Inconsistências no `AlfaFornecedorDTO`
| Campo DTO | Esperado pelo DTO (JSON property) | Real no Payload (`alfaEnergiaPayload.json`) | Status |
| :--- | :--- | :--- | :--- |
| `cnpj` | `cnpj` (implícito) | `tax_id` | ❌ Incorreto (Falta `@JsonProperty("tax_id")`) |
| `nome` | `nome` (implícito) | `name` | ❌ Incorreto (Falta `@JsonProperty("name")`) |

### Inconsistências no `AlfaItemDTO`
| Campo DTO | Esperado pelo DTO (JSON property) | Real no Payload (`alfaEnergiaPayload.json`) | Status |
| :--- | :--- | :--- | :--- |
| `linha` | `linha` (implícito) | `line` | ❌ Incorreto (Falta `@JsonProperty("line")`) |
| `codigoMaterial` | `codigo_material` | `material` | ❌ Incorreto (Deveria ser `@JsonProperty("material")`) |
| `descricao` | `descricao` (implícito) | `description` | ❌ Incorreto (Falta `@JsonProperty("description")`) |
| `unidadeMedida` | `unidade_medida` | `uom` | ❌ Incorreto (Deveria ser `@JsonProperty("uom")`) |
| `quantidadePedida` | `quantidade_pedida` | `quantity_ordered` | ❌ Incorreto (Deveria ser `@JsonProperty("quantity_ordered")`) |
| `quantidadeRecebida`| `quantidade_recebida` | `quantity_received`| ❌ Incorreto (Deveria ser `@JsonProperty("quantity_received")`) |
| `precoUnitario` | `preco_unitario` | `unit_price` | ❌ Incorreto (Deveria ser `@JsonProperty("unit_price")`) |

## 3. Formato de Data (Design vs. Realidade)

- **Documentação (`DESING.md` e Javadoc do DTO):** Afirma que "O Alfa envia data em ISO 8601 ('2026-08-15T00:00:00Z') — Jackson desserializa Instant diretamente, sem necessidade de conversão" (Seção 6).
- **Payload Real:** O campo `created_at` vem no formato `YYYY-MM-DD` (ex: `"2026-08-05"`), sem informação de tempo (hora) e fuso horário.
- **Problema Crítico:** O tipo `Instant` no Java exige um formato ISO-8601 completo (com T e Z) para desserialização automática. Ao tentar ler `"2026-08-05"`, o Jackson lançará uma exceção de desserialização (`InvalidFormatException`).
- **Solução Necessária:** Alterar o tipo de `dataCriacao` no DTO para `LocalDate` e realizar a conversão para `Instant` ou `ZonedDateTime` no `AlfaIngestor` (adicionando horário de início do dia e fuso horário padrão, por exemplo), ou adicionar um formatador específico no DTO. O `DESING.md` (seção 6) deve ser atualizado para refletir a necessidade de tratamento desta data.
