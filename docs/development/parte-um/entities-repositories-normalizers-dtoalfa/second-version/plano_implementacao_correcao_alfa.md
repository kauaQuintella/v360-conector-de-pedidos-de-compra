# Plano de Implementação — Correção da Integração Alfa

**Data:** 2026-09-06
**Baseado em:** `docs/analise_inconsistencias_alfa.md` + `public/alfaEnergiaPayload.json` + `docs/desing/DESING.md`

---

## O que foi pedido

Analisar o campo `linha` na entidade `Item` (para definir seu tipo correto no DTO) e gerar este plano detalhado e exato com todas as decisões de implementação — arquivos, diffs e código resultante completo — para validação antes de qualquer alteração ser aplicada.

---

## Entendimento do Problema

O projeto possui **três inconsistências críticas** entre o que o código espera e o que o cliente Alfa de fato envia:

### 1. Estrutura raiz do payload
O `AlfaController` usa `@RequestBody AlfaPedidoDTO`, esperando um único pedido na raiz do JSON. O cliente Alfa envia:
```json
{ "purchase_orders": [ { ...pedido... } ] }
```
O Jackson tentará desserializar o objeto raiz como `AlfaPedidoDTO` e não encontrará nenhum campo reconhecível na raiz — encontrará apenas `purchase_orders`. Resultado: **todos os campos do DTO ficarão nulos e a validação `@NotBlank`/`@NotNull` lançará exceção 400**, sem processar nenhum pedido.

### 2. Nomes de campos JSON incorretos nos DTOs
Os DTOs usam nomes em português sem `@JsonProperty`, mas o payload real está em inglês. O Jackson usa o nome do campo Java como chave JSON por padrão. Resultado: **todos os campos sem anotação ficam nulos após desserialização**.

Exemplo: `String moeda` tentará ler a chave `"moeda"` do JSON, mas o payload envia `"currency"` — o campo fica `null`, quebrando a validação `@NotBlank`.

### 3. Tipo de `dataCriacao` incompatível
O DTO declara `Instant dataCriacao` e o Javadoc afirma que o Alfa envia ISO 8601 completo. O payload real envia `"2026-08-05"` (apenas data, sem hora nem fuso). O Jackson não consegue construir um `Instant` a partir de uma string sem offset — lança `InvalidFormatException` antes da validação de bean.

---

## Análise do Campo `linha` na Entidade `Item`

**Resultado da análise:** A entidade `Item.java` (linha 43) declara:

```java
@Column(name = "linha")
private String linha;
```

**O campo `linha` é `String` na entidade e no schema SQL (`VARCHAR(50)`).**

No payload real, `linha` vem como número inteiro:
```json
{ "line": 10 }
```

**Decisão:** **Manter `String` no DTO e na entidade**, sem conversão explícita.

**Justificativa:**
- Alterar para `Integer` na entidade exigiria migração de banco, fora do escopo desta tarefa.
- O Jackson converte `10` (inteiro JSON) para `String` Java automaticamente quando o campo destino é `String` — comportamento padrão e seguro.
- `linha` é um identificador ordinal (não é operado matematicamente), então `String` é semanticamente adequado.

---

## Decisão Arquitetural: Onde fica a Conversão de Data?

O `DESING.md` (seção 1.1 e 1.2) define explicitamente:

> **Fluxo:** Ingestores → **Normalizadores compartilhados (CnpjSanitizer, StatusMapper, DateParser, UnitConverter)** → PedidoService
>
> **Normalizadores:** CNPJ, status, **data**, conversão de unidade.
>
> **Ingestor:** Traduz DTO bruto → entidade de domínio, **usando os normalizadores**. Não faz normalização por conta própria.

A conversão `LocalDate → Instant` é uma operação de **normalização de data** — ela deve viver em um normalizador dedicado (`AlfaDateParser`), não dentro do `AlfaIngestor`. O padrão já está estabelecido: o `AlfaIngestor` delega status ao `AlfaStatusMapper` e CNPJ ao `CnpjSanitizer`; a data deve seguir o mesmo padrão.

---

## Decisões de Implementação Exatas

### ETAPA 1 — Criar `AlfaPayloadDTO.java` (arquivo novo)

**Caminho:** `src/main/java/com/v360/prosel/conectorpedidoscompra/dto/alfa/AlfaPayloadDTO.java`

**Conteúdo exato a ser criado:**

```java
package com.v360.prosel.conectorpedidoscompra.dto.alfa;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * DTO wrapper do payload de entrada do cliente Alfa.
 *
 * O Alfa envia múltiplos pedidos em uma única requisição sob a chave
 * "purchase_orders". Este record representa o objeto raiz do JSON.
 */
public record AlfaPayloadDTO(
        @JsonProperty("purchase_orders")
        @NotEmpty
        @Valid
        List<AlfaPedidoDTO> purchaseOrders
) {}
```

---

### ETAPA 2 — Alterar `AlfaPedidoDTO.java`

**Caminho:** `src/main/java/com/v360/prosel/conectorpedidoscompra/dto/alfa/AlfaPedidoDTO.java`

| O que muda | De | Para |
|---|---|---|
| Import de tipo de data | `import java.time.Instant;` | `import java.time.LocalDate;` |
| Javadoc (linhas 15-16) | "O Alfa envia data em ISO 8601 ('2026-08-15T00:00:00Z') — Jackson desserializa Instant diretamente..." | "O Alfa envia dataCriacao no formato YYYY-MM-DD. Desserializado como LocalDate; a conversão para Instant é responsabilidade do AlfaDateParser (normalizador)." |
| Tipo de `dataCriacao` | `Instant dataCriacao` | `LocalDate dataCriacao` |
| `moeda` | sem `@JsonProperty` | `@JsonProperty("currency")` |
| `fornecedor` | sem `@JsonProperty` | `@JsonProperty("vendor")` |
| `itens` | sem `@JsonProperty` | `@JsonProperty("items")` |

**Arquivo resultante completo:**

```java
package com.v360.prosel.conectorpedidoscompra.dto.alfa;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

/**
 * DTO raiz de entrada do Alfa para um pedido de compra completo.
 *
 * O Alfa envia dataCriacao no formato YYYY-MM-DD (ex: "2026-08-05").
 * Desserializado como LocalDate; a conversão para Instant é responsabilidade
 * do AlfaDateParser (normalizador).
 */
public record AlfaPedidoDTO(
        @JsonProperty("po_number")
        @NotBlank
        String numeroPedido,

        @JsonProperty("created_at")
        @NotNull
        LocalDate dataCriacao,

        @NotBlank
        String status,

        @JsonProperty("currency")
        @NotBlank
        String moeda,

        @JsonProperty("vendor")
        @NotNull
        @Valid
        AlfaFornecedorDTO fornecedor,

        @JsonProperty("items")
        @NotEmpty
        @Valid
        List<AlfaItemDTO> itens
) {}
```

---

### ETAPA 3 — Alterar `AlfaFornecedorDTO.java`

**Caminho:** `src/main/java/com/v360/prosel/conectorpedidoscompra/dto/alfa/AlfaFornecedorDTO.java`

| Campo | Antes | Depois |
|---|---|---|
| `cnpj` | sem `@JsonProperty` | `@JsonProperty("tax_id")` |
| `nome` | sem `@JsonProperty` | `@JsonProperty("name")` |

**Arquivo resultante completo:**

```java
package com.v360.prosel.conectorpedidoscompra.dto.alfa;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * DTO de entrada do Alfa para o bloco de fornecedor.
 * O CNPJ é recebido aqui possivelmente com máscara — o AlfaIngestor aplica
 * CnpjSanitizer antes de persistir (DESING.md seção 2).
 */
public record AlfaFornecedorDTO(
        @JsonProperty("tax_id")
        @NotBlank String cnpj,

        @JsonProperty("name")
        @NotBlank String nome
) {}
```

---

### ETAPA 4 — Alterar `AlfaItemDTO.java`

**Caminho:** `src/main/java/com/v360/prosel/conectorpedidoscompra/dto/alfa/AlfaItemDTO.java`

**Decisão sobre o tipo de `linha`:** Mantido como `String`. A entidade também é `String` e o Jackson converte inteiro JSON → `String` Java automaticamente.

| Campo | `@JsonProperty` atual | `@JsonProperty` correto |
|---|---|---|
| `linha` | ausente | `"line"` |
| `codigoMaterial` | `"codigo_material"` | `"material"` |
| `descricao` | ausente | `"description"` |
| `unidadeMedida` | `"unidade_medida"` | `"uom"` |
| `quantidadePedida` | `"quantidade_pedida"` | `"quantity_ordered"` |
| `quantidadeRecebida` | `"quantidade_recebida"` | `"quantity_received"` |
| `precoUnitario` | `"preco_unitario"` | `"unit_price"` |

**Arquivo resultante completo:**

```java
package com.v360.prosel.conectorpedidoscompra.dto.alfa;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * DTO de entrada do Alfa para cada linha de item do pedido.
 * Todos os campos numéricos usam BigDecimal para preservar precisão (DESING.md 3.3).
 * O campo "line" vem como inteiro no JSON e é desserializado como String pelo Jackson.
 */
public record AlfaItemDTO(
        @JsonProperty("line")
        @NotBlank
        String linha,

        @JsonProperty("material")
        @NotBlank
        String codigoMaterial,

        @JsonProperty("description")
        String descricao,

        @JsonProperty("uom")
        String unidadeMedida,

        @JsonProperty("quantity_ordered")
        @NotNull
        BigDecimal quantidadePedida,

        @JsonProperty("quantity_received")
        @NotNull
        BigDecimal quantidadeRecebida,

        @JsonProperty("unit_price")
        @NotNull
        BigDecimal precoUnitario
) {}
```

---

### ETAPA 5 — Criar `AlfaDateParser.java` (normalizador — arquivo novo)

**Caminho:** `src/main/java/com/v360/prosel/conectorpedidoscompra/normalizer/AlfaDateParser.java`

**Responsabilidade:** Converter `LocalDate` (formato `YYYY-MM-DD` enviado pelo Alfa) em `Instant`, aplicando meia-noite no fuso `America/Sao_Paulo`. Segue o mesmo padrão de `CnpjSanitizer` (utilitário puro, sem estado, sem Spring).

**Conteúdo exato a ser criado:**

```java
package com.v360.prosel.conectorpedidoscompra.normalizer;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Utilitário puro (sem estado, sem Spring) para normalização de data do cliente Alfa.
 *
 * O Alfa envia datas no formato YYYY-MM-DD, sem informação de hora ou fuso horário.
 * Este normalizador converte LocalDate → Instant aplicando meia-noite (00:00:00)
 * no fuso padrão America/Sao_Paulo, alinhado com o contrato interno V360
 * que armazena datas como TIMESTAMPTZ (DESING.md seção 2 e 3).
 */
public class AlfaDateParser {

    static final ZoneId FUSO_PADRAO = ZoneId.of("America/Sao_Paulo");

    private AlfaDateParser() {
        // utilitário estático — instanciação desnecessária
    }

    /**
     * Converte uma data sem hora (LocalDate) para um instante UTC,
     * aplicando meia-noite no fuso America/Sao_Paulo.
     *
     * Ex.: "2026-08-05" → 2026-08-05T03:00:00Z (UTC)
     *
     * @param data data bruta recebida do Alfa
     * @return Instant correspondente ao início do dia no fuso padrão, ou null se a entrada for null
     */
    public static Instant parse(LocalDate data) {
        if (data == null) return null;
        return data.atStartOfDay(FUSO_PADRAO).toInstant();
    }
}
```

---

### ETAPA 6 — Alterar `AlfaIngestor.java`

**Caminho:** `src/main/java/com/v360/prosel/conectorpedidoscompra/ingestor/AlfaIngestor.java`

**Apenas uma linha muda** em `mapPedido`:

```java
// De:
pedido.setDataCriacao(dto.dataCriacao());

// Para:
pedido.setDataCriacao(AlfaDateParser.parse(dto.dataCriacao()));
```

**Nenhuma constante de `ZoneId` entra no Ingestor.** A responsabilidade do fuso fica totalmente encapsulada no `AlfaDateParser`.

**Método `mapPedido` resultante completo:**

```java
private Pedido mapPedido(AlfaPedidoDTO dto, Fornecedor fornecedor) {
    Pedido pedido = new Pedido();
    pedido.setNumeroPedidoOrigem(dto.numeroPedido());
    pedido.setClienteOrigem(CLIENTE_ORIGEM);
    pedido.setDataCriacao(AlfaDateParser.parse(dto.dataCriacao()));
    pedido.setStatus(statusMapper.map(dto.status()));
    pedido.setMoeda(dto.moeda());
    pedido.setFornecedor(fornecedor);
    // dataIngestao é preenchida pelo PedidoService no momento do upsert
    return pedido;
}
```

**Import a adicionar em `AlfaIngestor`:**
```java
import com.v360.prosel.conectorpedidoscompra.normalizer.AlfaDateParser;
```

---

### ETAPA 7 — Alterar `AlfaController.java`

**Caminho:** `src/main/java/com/v360/prosel/conectorpedidoscompra/controller/AlfaController.java`

**Alterações exatas:**

1. Substituir import de `AlfaPedidoDTO` por `AlfaPayloadDTO`:
```java
// Remover:
import com.v360.prosel.conectorpedidoscompra.dto.alfa.AlfaPedidoDTO;
// Adicionar:
import com.v360.prosel.conectorpedidoscompra.dto.alfa.AlfaPayloadDTO;
```

2. Adicionar import para `List`:
```java
import java.util.List;
```

3. Substituir o método `ingestirAlfa` completo:

```java
/**
 * Recebe um payload do cliente Alfa contendo uma lista de pedidos de compra,
 * traduz cada pedido via AlfaIngestor e persiste via PedidoService (upsert).
 *
 * @param payload payload validado pelo Bean Validation, contendo a lista purchase_orders
 * @return lista de resultados, um por pedido processado
 */
@PostMapping("/alfa")
@ResponseStatus(HttpStatus.OK)
public List<Map<String, Object>> ingestirAlfa(@RequestBody @Valid AlfaPayloadDTO payload) {
    return payload.purchaseOrders().stream()
            .map(dto -> {
                Pedido pedido = alfaIngestor.toEntity(dto);
                Pedido salvo = pedidoService.upsert(pedido);
                return Map.<String, Object>of(
                        "id_pedido",             salvo.getId(),
                        "numero_pedido_origem",   salvo.getNumeroPedidoOrigem(),
                        "cliente_origem",         salvo.getClienteOrigem(),
                        "status",                 salvo.getStatus()
                );
            })
            .toList();
}
```

---

## Resumo das Alterações por Arquivo

| Arquivo | Ação | Motivo |
|---|---|---|
| `dto/alfa/AlfaPayloadDTO.java` | **Criar** | Wrapper para `purchase_orders` (raiz do JSON real) |
| `dto/alfa/AlfaPedidoDTO.java` | **Alterar** | 3 `@JsonProperty` ausentes + `Instant` → `LocalDate` + Javadoc |
| `dto/alfa/AlfaFornecedorDTO.java` | **Alterar** | 2 `@JsonProperty` ausentes (`tax_id`, `name`) |
| `dto/alfa/AlfaItemDTO.java` | **Alterar** | 7 `@JsonProperty` incorretos ou ausentes |
| `normalizer/AlfaDateParser.java` | **Criar** | Normalizador de data `LocalDate → Instant` (respeita DESING.md seção 1.2) |
| `ingestor/AlfaIngestor.java` | **Alterar** | Delegar conversão de data ao `AlfaDateParser` |
| `controller/AlfaController.java` | **Alterar** | Receber `AlfaPayloadDTO` e iterar sobre a lista |

**Nenhuma alteração em entidades, repositórios, serviços ou banco de dados é necessária.**
