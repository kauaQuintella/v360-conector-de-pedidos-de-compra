## Decisões a fechar antes de codar

**1. Semântica do filtro `fornecedor`.** Não está definido se é busca exata por CNPJ ou por nome (parcial, `LIKE`). São implementações diferentes: CNPJ é `=` direto contra o campo já sanitizado; nome exige `LIKE %valor%` e é mais custoso. O mais simples é aceitar CNPJ (só dígitos, mesma sanitização da ingestão) e deixar busca por nome fora do escopo mínimo.

**2. Semântica do `com_pendencia`.** Três interpretações possíveis pro parâmetro:
- omitido → sem filtro;
- `true` → só pedidos com pelo menos um item com `quantidade_pendente > 0`;
- `false` → só pedidos com todos os itens zerados (pendência zero).

Vamos usar assim os filtros.

**3. Paginação.** Fora do escopo mínimo (já classificado como diferencial no planejamento geral). Não implemente agora — retornar lista completa é aceitável pro volume de teste do desafio. Não vale gastar tempo aqui dado o aperto de calendário.

## Filtros dinâmicos — abordagem técnica

Com 4 filtros opcionais e combináveis (a PDF pede "filtros úteis", no plural, ou seja, combinação simultânea, não um de cada vez), escolhi uma opcão:

- **JPQL com `:param IS NULL OR campo = :param`** — mais simples de escrever pra esse número pequeno de filtros, sem dependência extra.

Pelo escopo e prazo, JPQL condicional resolve bem. O ponto de atenção é o `com_pendencia`: ele não é uma coluna do `Pedido`, exige `EXISTS` contra `Item`:

```
EXISTS (SELECT 1 FROM Item i WHERE i.pedido = p AND i.quantidadePendente > 0)
```

Isso não combina naturalmente com o padrão `:param IS NULL OR ...` porque não há uma coluna simples pra comparar — o jeito mais limpo é ter duas queries no repositório (uma com a subcláusula `EXISTS`, outra sem) e o service decide qual chamar dependendo se `com_pendencia` veio ou não. Evita JPQL condicional excessivamente aninhado.

## DTOs de resposta

Não expor as entidades diretamente (já estava no plano). Dois formatos:

- **Lista (`GET /pedidos`)**: resumo — `id_pedido`, `numero_pedido`, `cliente_origem`, `status`, `fornecedor.cnpj`, `fornecedor.nome`, `moeda`, `data_criacao`. Sem itens.
- **Detalhe (`GET /pedidos/{id}`)**: tudo do resumo + lista de itens com `quantidade_pedida`, `quantidade_recebida`, `quantidade_pendente`, `preco_unitario`.

## Fetch do detalhe — cuidado com N+1

Se `Pedido.itens` for `@OneToMany` com `FetchType.LAZY` (padrão recomendado), buscar o pedido e depois acessar `.getItens()` fora da mesma transação estoura `LazyInitializationException`. Resolver com `JOIN FETCH` explícito na query do repositório (`SELECT p FROM Pedido p JOIN FETCH p.itens WHERE p.id = :id`) ou `@EntityGraph`. Fazer isso já no service, não deixar a serialização do controller descobrir o problema.

Sobre `quantidade_pendente`: como é coluna gerada no banco, ela só reflete o valor correto se o objeto veio de uma leitura fresca do banco — o que é o caso natural aqui, já que são endpoints de consulta pura, sem escrita na mesma transação. Não precisa de `refresh()` nem recálculo manual, só não reaproveitar uma entidade que ainda está em memória de uma escrita anterior no mesmo request.

## Status HTTP

- `GET /pedidos` sem resultados → `200` com lista vazia, nunca `404`.
- `GET /pedidos/{id}` inexistente → `404`, com corpo de erro padronizado (esse é o caso de erro técnico de recurso, diferente do `POST /notas-fiscais/conferir`, onde "não encontrado" é resultado de negócio e responde `200`).

## Testes mínimos a cobrir

- Cada filtro isolado e pelo menos uma combinação de dois filtros simultâneos.
- `com_pendencia=true` com um pedido totalmente recebido (não deve aparecer) e um parcialmente recebido (deve aparecer).
- Detalhe com pedido de múltiplos itens, conferindo que `quantidade_pendente` bate com `pedida - recebida` de cada linha.
- `GET /pedidos/{id}` com id inexistente → `404`.