# Relatório — conferência e consulta (Parte 1)

## Chave `(numero_pedido, cliente_origem)`

A nota fiscal exige os dois campos. A conferência localiza o pedido pela mesma unique do upsert. Sem `cliente_origem`, dois ERPs com o mesmo número colidiriam e a V360 não saberia qual pedido conferir.

Pedido não encontrado é **resultado de negócio**: HTTP 200, `Conferencia` `REJEITADA` com `PEDIDO_NAO_ENCONTRADO`. `GET /pedidos/{id}` inexistente continua 404 (recurso técnico).

## Órfãos

No reingresso, linha ausente do arquivo só é removida sem recebimento e sem `Divergencia` daquele `codigo_material` no pedido. Uma conferência qualquer do pedido (por exemplo só `FORNECEDOR_DIVERGENTE`) **não** congela todas as linhas.

## Agregação

A nota não cita `linha` interna. Quantidade e valor cruzam a soma de pendente e o preço ponderado por material.
