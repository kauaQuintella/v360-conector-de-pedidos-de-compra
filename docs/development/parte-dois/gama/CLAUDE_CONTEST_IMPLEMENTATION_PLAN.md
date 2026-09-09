Plano é sólido no que importa mais: ele leu o código real (`PedidoIngestor#toEntity`, `StatusMapper#map(String)`, sanitizers estáticos) em vez de assumir, o que corrige exatamente o ponto fraco da minha proposta anterior — eu inventei `PedidoDominio`/`ingerir(...)` sem ver a interface real. Isso é uma correção genuína e válida, não cosmética. Dito isso, achei um ponto estrutural que vale ajustar antes de codar, e dois menores pra ter resposta pronta.

## O ponto estrutural: agrupamento deveria estar fora do `GamaIngestor`

O próprio `DESING_V3.md` (seção 1.2) separa duas camadas distintas para o Beta: **Parser** (`BetaCsvRecordAssembler` + `BetaCsvParser` — remontagem, OpenCSV, **join**) e **Ingestor** (`BetaIngestor` — DTO já pronto → entidade). O join por `NUMERO_PEDIDO` acontece inteiramente dentro do parser, produzindo `List<BetaPedidoDTO>` já aninhado (um `BetaPedidoDTO` por pedido, com `items` dentro) — o `BetaIngestor.toEntity` recebe um pedido inteiro de cada vez, nunca uma lista achatada pra desmembrar.

O plano corrigido faz diferente: cria um método extra `toEntities(List<GamaItemLinhaDTO> linhas)` **dentro do próprio `GamaIngestor`**, que agrupa por `ped` internamente. Isso mistura duas responsabilidades que no Beta estão separadas (desmembrar estrutura bruta vs. mapear DTO→entidade), e cria um problema concreto de tipos: o `PedidoIngestor<List<GamaItemLinhaDTO>>` genérico usa `List<GamaItemLinhaDTO>` pra significar "linhas de **um** pedido já agrupado", mas `toEntities` usa o **mesmo tipo estático** `List<GamaItemLinhaDTO>` pra significar "todas as linhas cruas da requisição, de **vários** pedidos". Nada no compilador impede alguém de chamar `toEntity()` direto com a lista inteira por engano — só a validação de consistência (que dispara por acaso, não por desenho) pegaria isso em runtime.

**Ajuste mínimo, sem descartar nada do plano:**

```java
public record GamaPedidoDTO(
    String ped,
    String cnpjFornecedor,
    String nomeFornecedor,
    Long dtCriacao,
    Integer situacao,
    List<GamaItemLinhaDTO> itens
) {}
```

Um agrupador estático simples (`GamaPayloadAgrupador` ou método estático em `GamaIngestor`, mas separado de `toEntity`) faz `List<GamaItemLinhaDTO>` (bruto, N pedidos) → `List<GamaPedidoDTO>` (agrupado, um por pedido) com a validação de consistência embutida aí. Só então `GamaIngestor implements PedidoIngestor<GamaPedidoDTO>` com `toEntity(GamaPedidoDTO)` — **contrato idêntico** ao `AlfaIngestor`/`BetaIngestor` (um DTO já pronto → uma entidade), sem tipo ambíguo. É uma mudança pequena (um record a mais, uma função a mais), mas fecha o único lugar onde o plano se desvia do padrão que ele mesmo diz estar seguindo.

## Bean Validation em `List<T>` — resolvendo a incerteza que o próprio plano deixou em aberto

O plano corretamente desconfia: "confirmar que `@Valid` no `List` valida cada elemento". Vale resolver isso antes de escrever o controller, não durante: `@Valid @RequestBody List<GamaItemLinhaDTO>` **nem sempre** casca a validação pra cada elemento — depende de versão. A forma garantida (Bean Validation 2.0+, container element constraint) é anotar o **argumento de tipo**, não só o parâmetro:

```java
public List<Map<String, Object>> ingestirGama(
        @RequestBody List<@Valid GamaItemLinhaDTO> linhas)
```

Repare no `@Valid` dentro do `<>`, não fora. Isso evita a alternativa que o próprio plano já descartou (wrapper record, que quebraria o JSON oficial) e resolve a incerteza sem gambiarra.

## Dois pontos menores — ter resposta pronta, não bloqueiam

**`GamaStatusMapper` não implementa `StatusMapper`.** Correto dado que a interface é `map(String)` e Gama recebe `Integer` — mudar a interface pra genérica tocaria Alfa/Beta, contra Open/Closed. Mas isso cria uma assimetria real: dois clientes usam uma abstração comum pra status, um não. Se perguntarem "por que o Gama não segue o mesmo contrato de status dos outros", a resposta é exatamente essa troca — não reabrir a interface compartilhada vs. manter uma abstração 100% uniforme. Escolha defensável, só não deixe implícita.

**`"UN"` fixo para todo item do Gama.** Bate com os três itens do payload real (dois convertidos de `CX`, um que já é `UN` com `fator_conv=1`), mas é uma leitura um pouco mais forte do que a regra de negócio pede: a regra é "persistir a unidade da nota", não "persistir sempre a string UN". Coincide neste payload porque o PDF já enquadra a conversão de caixa como "unidades". Se algum dia aparecesse um `fator_conv=1` com `um` diferente de `UN` (não existe neste payload), o hardcode sobrescreveria incorretamente. Não vale complicar o código por um caso hipotético fora dos dados reais — só tenha a frase pronta: "fixei UN porque é o que o desafio narra explicitamente pra conversão do Gama, não uma regra geral de unidade."
