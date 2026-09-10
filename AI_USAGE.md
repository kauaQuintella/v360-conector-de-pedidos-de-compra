# AI USAGE

## 1. Ferramentas de IA usadas e em quais partes do desafio

| Ferramenta | Modelo                                                                                                   | Etapa do projeto                                                                                                                          | Propósito                                                                                                                |
|---|----------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------|
| **Gemini** | Usado para pesquisa e validacão de código (no comeco); **Gemini Pro High Effort** para revisão de código | Concepção inicial (leitura do desafio, README.md inicial, entendimento dos formatos de dados de Alfa/Beta/Gama), esquema inicial do Postgres | Pesquisa e rascunho de arquitetura;                                                                                      |
| **Claude Sonnet 5** (pensamento Alto) | Via Antigravity e depois via CLI                                                                         | Crítica e correção da arquitetura proposta pelo Gemini; geração e correção de planos de implementação                                     | Identificação de falhas na arquitetura sugerida pelo Gemini; desenvolvimento de código; validação de decisões de design; |
| **Cursor** | Grok 4.6 Medium                                                                                          | Usado a partir da implementacão de Beta                                                                                                   | Auxílio na geracão de código a partir de plano de implementacão refatorado;                                              |

**Fluxo de trabalho geral:** Brainstorm / Pesquisa de estratégias para o problema (Claude / Gemini / Grok) → Validacão 
cruzada entre IAs (Grok x Cursor) → Cursor/Antigravity para geração de código a partir de planos já validados → 
Gemini, Claude e Grok para revisão de output. Nenhuma IA implementou direto sobre o desenho sem um plano escrito revisado antes.


## 2. Exemplo em que a IA me levou por um caminho incompleto

**Contexto:** ao testar reingestão do Beta, percebi que o nome de um fornecedor continuava salvo com `\n` no meio (`Metalúrgica São Jorge\nS.A.`) mesmo depois de reingerir.

**Hipótese inicial:** associei o sintoma à camada de sanitização do `Ingestor` — já que `StringSanitizer`/`CnpjSanitizer` vivem ali, suspeitei que a normalização daquele cliente estivesse falhando na reingestão.

**O que a IA (Claude) sugeriu:** pedi para o Claude analisar a causa e propor um plano. O problema foi que usei o 
Claude sem contexto do projeto como estava implementado. A recomendação foi centralizar a normalização (sanitização de string, CNPJ etc.) na camada de Service (`PedidoService`), tirando essa responsabilidade de cada `Ingestor`.

**O que fiz e por que deu errado:** implementei a mudança sugerida. O resultado gerou acoplamento visível entre `PedidoService` e lógica de normalização específica de cliente — e não resolveu o problema original; o `\n` continuava aparecendo. Ao revisar o próprio resultado, identifiquei um problema mais sério que o sintoma: a camada de `Ingestor` deve se conectar exclusivamente com a camada de ingestão (DTO bruto → entidade); mover normalização para o Service criava dupla responsabilidade — o Service passava a orquestrar persistência **e** normalizar dado bruto, ferindo a separação de camadas já definida no projeto.

**Como corrigi o rumo:** reverti a centralização no Service. Reconsiderei a causa a partir da minha própria leitura do problema — descartei a hipótese de sanitização/Ingestor e passei a suspeitar do parser (`BetaCsvParser`). Levei essa interpretação para o Claude e pedi uma análise de `BetaCsvParser`/`BetaCsvRecordAssembler` com base nela. A causa real apareceu: o `CSVParserBuilder` do OpenCSV usa `\` como caractere de escape por padrão, e o CSV do Beta não segue nenhuma convenção de escape — um `\` literal no dado (o `\n` digitado no nome do fornecedor) era interpretado como "escape o próximo caractere", descartando a barra.

**O que fiz a respeito:** corrigi com o Claude apenas a linha do `BetaCsvParser` que configura o parser OpenCSV, desligando `escapeChar` e `quoteChar` (`CSVParser.NULL_CHARACTER`). Não mexi em mais nada — nem no Ingestor, nem no Service, nem nos normalizadores, que já estavam corretos desde o início.

**O que isso mostra:** a primeira sugestão de IA parecia razoável e eu cheguei a aplicá-la, mas era arquiteturalmente errada — violava a separação Ingestor/Service que o projeto já tinha fechado, e nem sequer resolveu o sintoma. Reverter, entender por que a mudança era estruturalmente ruim, e só então perseguir uma causa raiz diferente evitou deixar uma decisão de arquitetura equivocada no código.


## 3. Como garanti que entendo o código que estou entregando

**Documentação viva como controle de entendimento, não só de contexto para IA.** Mantive arquivos `.md` (`DESING.md`, depois `DESING_V3.md`, `ANALISE_ESTRUTURAL_PROJETO.md`, planos de implementação por cliente) que registram não só decisões, mas o *porquê* de cada uma. Isso serviu duplamente: alimentava contexto pras IAs, mas também me obrigava a formalizar — antes de aceitar qualquer código gerado — se eu conseguia explicar a decisão por escrito. Quando eu não conseguia justificar uma decisão de arquitetura no `.md`, isso era sinal de que eu ainda não tinha entendido de fato o que a IA tinha implementado.

**Nenhuma implementação direto sobre desenho não revisado.** Como descrito no fluxo de trabalho (seção 1), nenhuma IA gerou código a partir de uma ideia solta — sempre havia um plano escrito primeiro, e esse plano passava por pelo menos uma segunda IA (ou por mim, confrontando com o `DESING.md`) antes da implementação. Isso quebrava o processo em pontos de checagem: eu não avançava pra próxima etapa sem entender por que a etapa anterior tinha sido decidida daquele jeito — não só o que tinha sido implementado.

**Momento concreto: ciclo do Gama.** O plano inicial gerado pelo Claude (`IMPLEMENTATION_CLAUDE.md`) usava um tipo `PedidoDominio`/`ingerir(...)` inventado — o próprio texto admitia não ter visto o `PedidoIngestor<T>` real do projeto. Passei o plano pro Cursor contestar; ele corrigiu esse ponto, mas moveu o agrupamento por `ped` pra dentro do próprio `GamaIngestor` (`CURSOR_CONTEST_IMPLEMENTATION.md`). Antes de aceitar, levei essa versão de volta pro Claude contestar de novo — e ele apontou um segundo problema: essa mistura de responsabilidades contrariava a separação Parser/Ingestor que o `DESING_V3.md` já define pro Beta (`CLAUDE_CONTEST_IMPLEMENTATION_PLAN.md`). Checar essa segunda contestação contra o design doc, em vez de simplesmente aceitar a crítica mais recente, foi o que me obrigou a entender por que aquela separação existe antes de fechar o plano (`CURSOS_FINAL_IMPLEMENTATION_PLAN.md`).


## 4. Exemplo em que o resultado da IA foi direto ao ponto

**Contexto:** depois do ciclo de contestação Claude ↔ Cursor (seção 2 e 3), o plano ficou consolidado em `CURSOS_FINAL_IMPLEMENTATION_PLAN.md` — DTOs, `GamaPayloadAgrupador`, normalizadores, `GamaIngestor`, controller, testes e checklist de regressão, já com os caminhos reais do projeto.

**O prompt:** passei esse único documento pro Cursor como prompt de implementação, pedindo pra seguir o plano como estava, sem reabrir decisões já fechadas.

**O que saiu:** a partir só desse `.md`, o Cursor implementou a ingestão do Gama de ponta a ponta — DTOs, agrupador, 
normalizadores, ingestor, controller e testes — sem eu precisar depois corrigir nenhum código e/ou nenhuma decisão de 
arquitetura depois. A suíte de regressão do Alfa/Beta continuou passando.

**Por que aproveitei quase como veio:** o plano só chegou nesse nível de detalhe porque já tinha passado pelas duas rodadas de contestação da seção 3 — o resultado direto ao ponto aqui não veio de um prompt bem escrito na hora, veio do processo de validação cruzada anterior. Dá pra ver o contraste com a seção 3: IA implementando direto de uma ideia solta erra estrutura; IA implementando a partir de um plano já validado acerta de primeira.