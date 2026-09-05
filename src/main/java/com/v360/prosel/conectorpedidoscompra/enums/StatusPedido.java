package com.v360.prosel.conectorpedidoscompra.enums;

/**
 * Enum único de status de pedido do contrato V360.
 * Cada cliente tem seu vocabulário de origem; a tradução é responsabilidade
 * do StatusMapper específico de cada ingestor (DESING.md seção 2 e 6).
 */
public enum StatusPedido {
    OPEN,
    CLOSED,
    BLOCKED
}
