---
name: Ingestão Gama Logística
overview: "Ingestão Gama alinhada ao Alfa/Beta: DTO de linha + agrupador (join por ped) + GamaIngestor<GamaPedidoDTO> + upsert existente. Bean Validation no argumento de tipo da List. UN e StatusMapper assimétrico ficam documentados, sem reabrir a interface."
todos:
  - id: dto-gama
    content: Criar GamaItemLinhaDTO (JSON) e GamaPedidoDTO (pós-agrupamento, interno); teste de desserialização do payload oficial
    status: pending
  - id: agrupador-gama
    content: Criar GamaPayloadAgrupador (agrupar por ped + consistência de cabeçalho) com testes puros
    status: pending
  - id: normalizers-gama
    content: Criar GamaDateParser, GamaStatusMapper (Integer, sem mudar StatusMapper) e UnitConverter com testes dourados
    status: pending
  - id: ingestor-gama
    content: Implementar GamaIngestor como PedidoIngestor<GamaPedidoDTO> (só DTO pronto → Pedido JPA); testes com payload agrupado
    status: pending
  - id: controller-gama
    content: "GamaController POST /ingest/gama com List<@Valid GamaItemLinhaDTO>, agrupador, upsert e resposta igual Alfa/Beta"
    status: pending
  - id: regressao-readme
    content: Rodar suíte completa e anotar no README o que foi só adicionar vs. decisões Gama
    status: pending
isProject: false
---

# Ingestão Gama (Parte 2) — plano corrigido

Cópia de trabalho alinhada ao plano Cursor. Contestação do Claude incorporada (agrupador fora do ingestor; `List<@Valid ...>`).

## Veredito

O plano em [`IMPLEMENTATION_CLAUDE.md`](IMPLEMENTATION_CLAUDE.md) **é válido para implementar**, com correções. As observações em [`OBS_CLAUDE.md`](OBS_CLAUDE.md) estão certas (teste dourado TRP-01, converter também `qtd_rec`, array raiz, `fator_conv > 0`, consistência por `ped`).

A contestação em [`CLAUDE_CONTEST_IMPLEMENTATION_PLAN.md`](CLAUDE_CONTEST_IMPLEMENTATION_PLAN.md) **entra no plano**: agrupamento fora do ingestor (`GamaPedidoDTO` + `GamaPayloadAgrupador`) e Bean Validation no argumento de tipo (`List<@Valid ...>`). Os dois pontos menores (assimetria do `StatusMapper` e `"UN"` fixo) **não mudam o código** — só a justificativa para entrevista.

**Não copiar o esqueleto Java original do Claude.** Ele inventou `PedidoDominio` / `ingerir(...)` e não leu o Strategy real.

## O que o Claude acertou (manter)

- Ordem bottom-up: DTO → normalizadores puros → (agrupador) → ingestor → controller → regressão Alfa/Beta.
- Body de `POST /ingest/gama` = array JSON, não wrapper (igual [`public/gamaLogisticaPayload.json`](../../../../public/gamaLogisticaPayload.json)).
- Agrupar por `ped` com `LinkedHashMap`.
- `quantidade_recebida = qtd_rec × fator_conv` (lacuna da tabela da seção 6 do design; o JSON da seção 2 já faz 2×12=24).
- `@Positive` em `fator_conv` (evita divisão por zero → 500).
- Validar cabeçalho repetido divergente entre linhas do mesmo `ped` → `IllegalArgumentException`.
- Não alterar Alfa, Beta, conferência, schema ou `PedidoService`.

## O que o Claude errou (corrigir)

- `PedidoDominio` / `ItemDominio` — Strategy real é `PedidoIngestor<T>#toEntity` devolvendo `Pedido` JPA transiente.
- `StatusMapper<Integer>` — a interface é `map(String)`. Não alterar. `GamaStatusMapper` com `map(Integer)`.
- Métodos `sanitizar` — usar `CnpjSanitizer.sanitize` / `StringSanitizer.sanitize`.
- `linha.um()` como unidade da nota — persistir `"UN"`.
- Resposta `ok().build()` — replicar o mapa do Alfa/Beta.
- Validar só CNPJ + `situacao` — validar os quatro campos repetidos por `ped`.
- Agrupar **dentro** de `GamaIngestor.toEntities` — ver seção seguinte.

## Ajuste estrutural (contestação): agrupador fora do ingestor

O `DESING_V3.md` §1.2 separa Parser (Beta: join → `List<BetaPedidoDTO>`) de Ingestor (`toEntity` recebe um pedido já aninhado). `toEntities` no `GamaIngestor` mistura as camadas e deixa `List<GamaItemLinhaDTO>` ambíguo.

**Padrão:** JSON array → `GamaItemLinhaDTO` → `GamaPayloadAgrupador` → `GamaPedidoDTO` → `GamaIngestor implements PedidoIngestor<GamaPedidoDTO>`.

`GamaPedidoDTO` é interno (não é o body HTTP), no mesmo papel de `BetaPedidoDTO`.

## Decisões fechadas

- Moeda `"BRL"` constante.
- Unidade persistida sempre `"UN"` (o que o desafio narra para o Gama; `um` não vai ao banco).
- Cliente origem `"GAMA"`.
- Epoch segundos → `Instant`.
- Status 1/2/3; `GamaStatusMapper` não implementa `StatusMapper` (Open/Closed).
- Preço `(centavos / 100) / fator_conv`, HALF_UP, escala 4, intermediária 10.

## Implementação (só adicionar)

1. `GamaItemLinhaDTO` + `GamaPedidoDTO`
2. `GamaPayloadAgrupador` (parser, estático): group + consistência; lista vazia → IAE
3. `GamaDateParser`, `GamaStatusMapper`, `UnitConverter`
4. `GamaIngestor.toEntity(GamaPedidoDTO)` — sem `toEntities`
5. Controller: `@RequestBody List<@Valid GamaItemLinhaDTO> linhas` → agrupar → upsert
6. Testes + regressão Alfa/Beta + nota mínima no README

**Fora de escopo:** `git tag`, conferência, schema, alterar `StatusMapper`, DIA6 completo.

## Teste dourado

`TRP-01` / `GL-778`: 120 / 24 / pendente 96 / preço 100.00 / `UN` / `OPEN`.
