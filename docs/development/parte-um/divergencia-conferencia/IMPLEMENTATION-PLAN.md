# Plano — Divergência e conferência da Parte 1


## Problema e escopo


O Beta Alimentos já está implementado e persistindo pedidos, fornecedores e itens no
PostgreSQL. A próxima entrega deve fechar a Parte 1 com consulta dos pedidos,
conferência de notas fiscais, persistência do histórico de conferências/divergências
e relatório agregado. O DDL das tabelas `Conferencia` e `Divergencia` já existe;
não é necessário redesenhar o modelo de `Fornecedor`, `Pedido` ou `Item`.


O plano considera como fechadas as decisões do documento operacional:



- tolerância absoluta de R$ 0,05 para valor;

- `BLOCKED` e `CLOSED` geram divergência, mas não interrompem a avaliação;

- casamento de itens por soma de `quantidade_pendente` agrupada por
`codigo_material`, com preço ponderado pela pendência quando houver linhas
repetidas;

- itens órfãos só são removidos quando não têm recebimento nem histórico de
divergência/conferência.



## Sequência de implementação



1. **Consulta dos pedidos (DIA3)**



- Criar DTOs de resposta para não expor entidades JPA diretamente.

- Adicionar `GET /pedidos` com filtros opcionais
`cliente_origem`, fornecedor/CNPJ, `status` e `com_pendencia`.

- Adicionar `GET /pedidos/{id}` com fornecedor, itens e a pendência calculada
pelo PostgreSQL.

- Criar consultas de repositório/serviço que preservem a chave de origem e
permitam filtrar fornecedor sem carregar dados de forma inconsistente.

2. **Modelo de conferência**



- Mapear `Conferencia` e `Divergencia` como entidades JPA, com relacionamento
entre elas e `id_pedido` opcional para o caso de pedido inexistente.

- Criar repositórios para persistência e para as consultas históricas exigidas
pela regra de órfãos.

- Representar resultado e tipos de divergência com valores compatíveis com o
DDL existente, evitando alterações desnecessárias no schema.

3. **Correção preventiva do upsert**



- Depois de processar as linhas recebidas, comparar as linhas persistidas com
as linhas do payload atual.

- Para cada linha ausente, consultar `quantidade_recebida` e o histórico de
conferência/divergência pelos repositórios já mapeados no passo anterior.

- Remover explicitamente apenas o órfão sem recebimento e sem referência
histórica; preservar qualquer linha já movimentada ou usada no fluxo fiscal.

- Executar essa etapa antes da conferência para impedir pendência fantasma na
agregação por material, sem usar `clear()` como mecanismo de exclusão.

4. **Entrada e regras da conferência**



- Definir DTO validado da nota fiscal com `numero_pedido`,
`cliente_origem`, CNPJ do fornecedor e itens com código do material,
quantidade e valor total informado.

- Tornar `cliente_origem` obrigatório: a busca do pedido usa
`(numero_pedido, cliente_origem)`, exatamente como a chave única persistida.
Isso elimina a ambiguidade caso clientes distintos usem o mesmo número; a
plataforma V360 deve informar a origem junto com a nota.

- Registrar essa decisão no relatório documental final da implementação para
explicitar a motivação na entrevista.

- Implementar um serviço transacional que localize o pedido por essa chave e
acumule falhas numa lista antes de persistir.

- Aplicar, nessa ordem lógica, pedido inexistente, fornecedor, status,
materiais, quantidade pendente agregada e valor.

- Para cada material, somar as pendências de todas as linhas; comparar
quantidade da nota com esse saldo; calcular o preço de referência ponderado
pelo saldo pendente.

- Comparar valores com `BigDecimal` usando
`abs().compareTo(new BigDecimal("0.05")) > 0`; nunca usar `double`.

- Persistir uma `Conferencia` mesmo quando houver divergências e uma
`Divergencia` por falha; usar `APROVADA` somente com lista vazia e
`REJEITADA` caso contrário. Pedido inexistente registra apenas o que pode
ser determinado sem cruzar itens.

5. **Endpoints e relatório**



- Expor `POST /notas-fiscais/conferir` com resposta HTTP **200** mesmo quando
o pedido não existir: pedido inexistente é resultado de negócio, portanto
gera uma `Conferencia` `REJEITADA` persistida com
`PEDIDO_NAO_ENCONTRADO`, e não um erro HTTP 404.

- Expor `GET /relatorios/conferencias` com contagens totais por resultado e
por tipo de divergência, consultadas do histórico persistido.

- Expor a consulta `GET /pedidos/{id}` com **404** quando o recurso técnico
não existir; esse tratamento não deve ser reutilizado automaticamente no
endpoint de conferência.

- Padronizar somente erros técnicos de validação/payload e consulta conforme
o estilo REST já usado no projeto; não transformar rejeições de negócio em
exceções HTTP.

6. **Testes, documentação e fechamento**



- Testar cada tipo de divergência isoladamente e combinações no mesmo pedido,
incluindo tolerância de preço, múltiplas linhas do mesmo material,
`BLOCKED`/`CLOSED`, pedido inexistente com resposta 200 e aprovação sem
divergências.

- Testar filtros/detalhe, remoção condicional de órfãos e agregação do
relatório; preservar a regressão dos testes existentes.

- Atualizar o README com as quatro decisões de negócio, a exigência de
`cliente_origem` na nota, o contrato dos endpoints, a distinção entre
rejeição de negócio e erro HTTP e os dados persistidos pelo Beta versus os
dados da conferência.

- Criar um relatório Markdown da implementação com as decisões relevantes,
incluindo a chave `(numero_pedido, cliente_origem)` e a justificativa
contra buscas ambíguas.

- Após tudo validado, criar a tag `parte-1`; a tag não faz parte da lógica da
aplicação e não deve ser criada antes da conclusão.



## Componentes previstos



- `controller`: consulta, conferência e relatório.

- `dto`: filtros, detalhe de pedido, nota fiscal (incluindo
`cliente_origem`) e respostas de conferência.

- `entity`/`repository`: `Conferencia` e `Divergencia`, além das consultas
históricas necessárias ao upsert.

- `service`: consulta e `ConferenciaService`; pequena extensão cirúrgica no
`PedidoService` para órfãos.

- `src/main/resources/schema.sql`: apenas validar compatibilidade; reutilizar o
DDL atual salvo descoberta de erro concreto.

- `README.md` e testes correspondentes.



## Considerações



- O plano não inclui Gama, novas ingestões ou refatoração da etapa Beta.

- A ordem é intencional: entidades e repositórios de conferência vêm antes da
extensão do upsert porque o controle de órfãos depende da consulta histórica.

- A conferência deve operar sobre o PostgreSQL real para validar a coluna gerada
`quantidade_pendente`; H2 fica restrito a testes que não dependam dessa
semântica.

- Paginação, vídeo e melhorias não essenciais ficam fora do fechamento mínimo da
Parte 1; consulta, conferência, relatório e regras de negócio são obrigatórios.