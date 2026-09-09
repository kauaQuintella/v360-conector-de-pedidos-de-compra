Cruzando o payload real com o `DESING_V3.md`, quatro coisas que a leitura da seção 6 sozinha não deixa claras — e uma confirmação numérica que vale ouro pra defender a Decisão 1 na entrevista.

## 1. O exemplo do contrato único (seção 2) foi extraído literalmente desta linha do payload

Confira: `GL-778`, item `TRP-01`, `qtd_ped=10 × fator_conv=12 = 120`, `qtd_rec=2 × 12 = 24`, pendente `96`, `preco_unit_centavos=120000 ÷ 100 ÷ 12 = 100.00`. Todos os quatro números batem exatamente com o JSON de exemplo da seção 2 do design. Isso não é coincidência — é uma confirmação de que o modelo único já foi calibrado contra este payload real. Use isso a seu favor: **esse é seu teste dourado**. Depois de implementar o `GamaIngestor`, um `assertThat` comparando a resposta de `GET /pedidos/{id}` pra `GL-778`/`TRP-01` contra esses quatro valores prova a conversão de unidade de ponta a ponta, sem precisar inventar dado de teste.

## 2. Gap explícito na fórmula da seção 6: falta converter `quantidade_recebida`

A tabela da seção 6 escreve só `quantidade_pedida = qtd_ped × fator_conv`. Não menciona que `quantidade_recebida` precisa da mesma conversão (`qtd_rec × fator_conv`). O próprio exemplo da seção 2 já aplica isso corretamente (2×12=24), mas quem implementar lendo só a fórmula escrita — sem cruzar com o JSON — pode converter só a pedida e deixar a recebida em unidade de caixa. Resultado: `quantidade_pendente` (coluna gerada, `pedida − recebida`) sairia com pedida em unidades e recebida em caixas, uma subtração sem sentido dimensional. Vale corrigir essa lacuna no design antes de codar.

## 3. Prova numérica real da Decisão 1 (tolerância R$ 0,05) — não é só teoria

O item `TRP-09` (`GL-778`) é o caso que a Decisão 1 já previa abstratamente ("divisão por `fator_conv` gera dízimas"), só que agora com números de verdade:

- `preco_unit_centavos=10000` → R$100,00/caixa; `fator_conv=3` → `preco_unitario = 100.00 ÷ 3 = 33,3333` (arredondado em `NUMERIC(15,4)`).
- `quantidade_pedida = 4 × 3 = 12` unidades.
- Valor esperado pela conferência: `12 × 33,3333 = 399,9996`.
- Valor "exato" de origem: `4 caixas × R$100,00 = R$400,00`.
- Resíduo: `|400,00 − 399,9996| = R$0,0004`.

Isso está **100x dentro** da margem de R$0,05 — a decisão se sustenta com folga generosa, não no limite. Vale levar essa conta pronta pra entrevista: em vez de "decidi R$0,05 porque acho que cobre arredondamento", você mostra o resíduo real medido (R$0,0004) e explica que a margem escolhida é bem mais folgada que o necessário — postura ainda mais defensável.

## 4. O corpo de `POST /ingest/gama` é um array solto, não um objeto envolto

Diferente do Alfa (`{"purchase_orders": [...]}`), este JSON é `[ {...}, {...}, {...} ]` direto. Isso muda a assinatura do controller: `@Valid @RequestBody List<GamaItemLinhaDTO> linhas`, não um DTO wrapper com uma lista dentro. Vale confirmar que a validação em cascata funciona sobre `List<T>` (Spring valida cada elemento automaticamente com `@Valid` no parâmetro) antes de assumir que peguei certo — teste com uma linha inválida no meio do array pra confirmar que o Bean Validation dispara.

## 5. Integridade dos campos repetidos por `ped` — decisão nova, não coberta no design

Como não há arquivo de cabeçalho separado, `cnpj_fornecedor`, `nome_fornecedor`, `dt_criacao` e `situacao` se repetem em cada linha do mesmo `ped` (confirmado: as duas linhas de `GL-778` trazem valores idênticos). O design não decide o que fazer se essas repetições **divergirem** entre linhas do mesmo pedido — cenário que este payload de exemplo não testa (mas um `ped` com dado inconsistente entre linhas é plausível num sistema legado real). Dado o estilo defensivo já usado no `BetaCsvParser` (lança `IllegalArgumentException` em inconsistências), a escolha mais coerente com o resto do código é: pegar o valor da primeira linha como fonte de verdade, mas **validar** que as demais linhas do mesmo `ped` concordam, lançando erro se não concordarem — em vez de silenciosamente usar sempre a primeira sem checar.

## 6. Falta guarda contra `fator_conv = 0`

Este payload nunca traz `fator_conv=0`, mas a fórmula (`preco_unit_centavos/100 ÷ fator_conv`) quebra com `ArithmeticException`/divisão por zero se isso ocorrer. Vale `@Positive` ou `@Min(1)` no DTO, seguindo o mesmo padrão de validação de entrada já usado nos outros dois ingestors.

## 7. Efeito colateral útil: fecha um gap de teste antigo

`GL-779` (`situacao=2`/`CLOSED`, `qtd_rec=100` = `qtd_ped=100`) é um pedido **totalmente recebido**. Isso resolve uma lacuna de teste apontada há várias rodadas atrás: até agora nenhum pedido de teste tinha pendência zero, então `com_pendencia=false` nunca foi de fato diferenciado de "sem filtro". Depois de ingerir Gama, vale rodar esse teste específico contra `GL-779` — não é só requisito da Parte 2, é a peça que faltava pra validar aquele filtro de verdade.