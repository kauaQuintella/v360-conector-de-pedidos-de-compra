package com.v360.prosel.conectorpedidoscompra.ingestor.strategy;

import com.v360.prosel.conectorpedidoscompra.entity.Pedido;

/**
 * Contrato do padrão Strategy para ingestão de pedidos de compra.
 *
 * Cada cliente tem sua implementação específica (AlfaIngestor, BetaIngestor, GamaIngestor).
 * Adicionar um novo cliente não exige tocar nos ingestors existentes — OCP na prática.
 * (DESING.md seção 1.3)
 *
 * Responsabilidade do ingestor: traduzir o DTO bruto do cliente em entidades de domínio,
 * aplicando os normalizadores compartilhados. Persistência é responsabilidade do PedidoService.
 *
 * @param <T> tipo do DTO de entrada do cliente
 */
public interface PedidoIngestor<T> {

    /**
     * Traduz o DTO bruto para uma entidade Pedido (transiente — não persistida).
     * O clienteOrigem deve ser setado pelo ingestor.
     *
     * @param dto payload bruto recebido do cliente
     * @return Pedido transiente pronto para upsert via PedidoService
     */
    Pedido toEntity(T dto);
}
