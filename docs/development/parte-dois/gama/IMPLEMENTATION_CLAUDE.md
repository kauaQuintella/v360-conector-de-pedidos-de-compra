Ordem bottom-up: peças isoladas e testáveis sem Spring primeiro, integração por último — mesmo padrão que já funcionou pra Beta (`BetaCsvRecordAssembler`/`BetaNumberParser` testados puros antes do `BetaIngestor`).

## 0. Decisões a fechar antes de escrever código

Duas perguntas do apontamento anterior que mudam a lógica do `GamaIngestor`, então segue aqui as respostas sobre:

1. **Campos repetidos por `ped` divergem entre linhas** (ex.: duas linhas do mesmo `ped` com `cnpj_fornecedor` 
   diferente) →  validar e lançar `IllegalArgumentException`. Validar, consistente com o estilo defensivo do 
   `BetaCsvParser`.
2. **`fator_conv` ausente, zero ou negativo** → `@Positive`/`@Min(1)` no DTO (falha na validação do Bean Validation, vira 400 automaticamente) em vez de deixar a divisão estourar `ArithmeticException` (que cairia no handler genérico como 500).

Decidido isso, a ordem:

## 1. DTO (`dto/gama/GamaItemLinhaDTO.java`)

Espelha o payload linha a linha — sem separar "cabeçalho" de "item", já que no Gama os dois vêm juntos em cada objeto:

```java
public record GamaItemLinhaDTO(
    @NotBlank String ped,
    @NotNull Integer item,
    @NotBlank String cnpjFornecedor,
    @NotBlank String nomeFornecedor,
    @NotNull Long dtCriacao,
    @NotBlank String codMat,
    @NotBlank String descMat,
    @NotBlank String um,
    @NotNull @Positive Integer fatorConv,
    @NotNull @Positive BigDecimal qtdPed,
    @NotNull @PositiveOrZero BigDecimal qtdRec,
    @NotNull @PositiveOrZero Long precoUnitCentavos,
    @NotNull Integer situacao
) {}
```

Confirme o `@JsonProperty` de cada campo contra o `snake_case` real do arquivo (`ped`, `cnpj_fornecedor` etc.) — Jackson não faz esse mapeamento automático sem `@JsonNaming` configurado globalmente ou anotação por campo.

**Teste:** desserializar `gamaLogisticaPayload.json` direto num `List<GamaItemLinhaDTO>` e conferir os 13 campos da primeira linha. Sem lógica ainda, só valida o mapeamento JSON→DTO.

## 2. Normalizadores isolados — testáveis sem grouping, sem Spring

**`GamaStatusMapper`** (implementa a interface `StatusMapper` já existente):
```java
public class GamaStatusMapper implements StatusMapper<Integer> {
    public StatusPedido map(Integer situacao) {
        return switch (situacao) {
            case 1 -> StatusPedido.OPEN;
            case 2 -> StatusPedido.CLOSED;
            case 3 -> StatusPedido.BLOCKED;
            default -> throw new IllegalArgumentException("Situação Gama desconhecida: " + situacao);
        };
    }
}
```
Teste: os três valores válidos + um inválido (ex. `0`, `4`) lançando exceção.

**`GamaDateParser`** (epoch segundos → `Instant`, isolado, sem interface genérica — decisão já registrada no design):
```java
public class GamaDateParser {
    public Instant parse(long epochSegundos) {
        return Instant.ofEpochSecond(epochSegundos);
    }
}
```
Teste: `1786752000` → confira a data resultante batendo com o que o payload espera (`2026-08-15T00:00:00Z`, conforme o exemplo da seção 2 do design).

**`UnitConverter`** (novo — ainda não existe):
```java
public class UnitConverter {
    public BigDecimal converterQuantidade(BigDecimal quantidadeOrigem, int fatorConv) {
        return quantidadeOrigem.multiply(BigDecimal.valueOf(fatorConv));
    }

    public BigDecimal converterPrecoUnitario(long precoUnitCentavos, int fatorConv) {
        return BigDecimal.valueOf(precoUnitCentavos)
                .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)
                .divide(BigDecimal.valueOf(fatorConv), 4, RoundingMode.HALF_UP);
    }
}
```
Escala intermediária de 10 casas antes de arredondar pra 4 evita perder precisão em dois `divide` sucessivos.

**Teste dourado** (usa os números reais do payload, já calculados na análise anterior):
- `converterQuantidade(10, 12)` → `120`; `converterQuantidade(2, 12)` → `24` (pendente = 96, bate com a seção 2 do design).
- `converterPrecoUnitario(120000, 12)` → `100.0000` exato.
- `converterPrecoUnitario(10000, 3)` → `33.3333` (a dízima real que sustenta a tolerância de R$0,05 — vale um teste específico confirmando esse valor exato, não só "diferente de zero").

## 3. `GamaIngestor` — agrupamento + validação de consistência

```java
public class GamaIngestor implements PedidoIngestor<List<GamaItemLinhaDTO>> {

    List<PedidoDominio> ingerir(List<GamaItemLinhaDTO> linhas) {
        Map<String, List<GamaItemLinhaDTO>> porPed = new LinkedHashMap<>();
        for (var linha : linhas) {
            porPed.computeIfAbsent(linha.ped(), k -> new ArrayList<>()).add(linha);
        }

        List<PedidoDominio> pedidos = new ArrayList<>();
        for (var entry : porPed.entrySet()) {
            List<GamaItemLinhaDTO> linhasDoPedido = entry.getValue();
            validarConsistencia(linhasDoPedido); // decisão do item 0.1
            GamaItemLinhaDTO primeira = linhasDoPedido.get(0);

            pedidos.add(new PedidoDominio(
                entry.getKey(),
                "GAMA",
                cnpjSanitizer.sanitizar(primeira.cnpjFornecedor()),
                stringSanitizer.sanitizar(primeira.nomeFornecedor()),
                dateParser.parse(primeira.dtCriacao()),
                statusMapper.map(primeira.situacao()),
                "BRL", // Gama não informa moeda no payload — confirmar suposição
                linhasDoPedido.stream().map(this::toItem).toList()
            ));
        }
        return pedidos;
    }

    private ItemDominio toItem(GamaItemLinhaDTO linha) {
        return new ItemDominio(
            String.valueOf(linha.item()),          // "linha" no domínio = "item" no payload
            linha.codMat(),
            linha.descMat(),
            linha.um(),                             // unidade da nota, não a de compra
            unitConverter.converterQuantidade(linha.qtdPed(), linha.fatorConv()),
            unitConverter.converterQuantidade(linha.qtdRec(), linha.fatorConv()), // gap do item 2 da análise anterior
            unitConverter.converterPrecoUnitario(linha.precoUnitCentavos(), linha.fatorConv())
        );
    }

    private void validarConsistencia(List<GamaItemLinhaDTO> linhasDoPedido) {
        var primeira = linhasDoPedido.get(0);
        for (var linha : linhasDoPedido) {
            if (!linha.cnpjFornecedor().equals(primeira.cnpjFornecedor())
                    || !linha.situacao().equals(primeira.situacao())) {
                throw new IllegalArgumentException(
                    "Dados de cabeçalho inconsistentes para o pedido " + linha.ped());
            }
        }
    }
}
```

**Ponto de atenção real:** não tenho o código do `PedidoIngestor<T>` nem do `PedidoService` (o método que Alfa/Beta chamam pra persistir — upsert de fornecedor/pedido/itens). O esqueleto acima assume um tipo intermediário `PedidoDominio`/`ItemDominio`, mas se `AlfaIngestor`/`BetaIngestor` já produzem direto as entidades JPA ou chamam `pedidoService.upsert(...)` com outra assinatura, ajusto pra bater exatamente. Compartilhe o `PedidoIngestor` e um dos dois ingestors existentes e eu fecho isso sem suposição.

**Moeda:** o payload do Gama não tem campo de moeda (diferente de Alfa/Beta, que trazem `currency`/`MOEDA`). O esqueleto assume `"BRL"` fixo — confirme se é isso mesmo ou se deveria vir de configuração.

**Teste do `GamaIngestor`:** rodar `gamaLogisticaPayload.json` completo → 2 pedidos (`GL-778` com 2 itens, `GL-779` com 1) e o teste dourado ponta a ponta (item 1 da análise anterior) comparando os 4 valores exatos.

## 4. Controller

```java
@RestController
@RequestMapping("/ingest/gama")
public class GamaController {
    @PostMapping
    public ResponseEntity<?> ingerir(@Valid @RequestBody List<GamaItemLinhaDTO> corpo) {
        gamaIngestor.ingerir(corpo);
        return ResponseEntity.ok().build(); // confirmar o formato de resposta usado por Alfa/Beta e replicar
    }
}
```
Teste manual: confirmar que a validação em cascata do Bean Validation dispara com uma linha inválida no meio do array (`fator_conv: 0`, por exemplo) — Spring valida elemento a elemento em `@Valid @RequestBody List<T>`, mas vale confirmar na prática antes de assumir.

## 5. Regressão

Depois de tudo isso passando, rodar a suíte completa de Alfa+Beta (~129 testes) sem tocar em nenhuma classe deles — se algum quebrar, é sinal de que algo do Gama vazou pra uma classe compartilhada de forma indevida (viola Open/Closed).

## 6. README + tag

Preencher a seção "o que foi só adicionar vs. o que exigiu mexer" com base no que realmente aconteceu na implementação (provavelmente: só adicionar, exceto se o `UnitConverter` ou a validação de consistência revelarem necessidade de ajustar algo genérico). `git tag parte-1` já deveria ter sido feita antes disso (Parte 1 estava completa); esta etapa fecha a Parte 2 — considere uma segunda tag ou só o commit final, conforme preferir.