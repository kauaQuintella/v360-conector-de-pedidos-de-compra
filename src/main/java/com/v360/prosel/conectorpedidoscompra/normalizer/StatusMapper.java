package com.v360.prosel.conectorpedidoscompra.normalizer;

import com.v360.prosel.conectorpedidoscompra.enums.StatusPedido;

/**
 * Contrato para tradução do status bruto de cada cliente para o enum único V360.
 * Cada cliente implementa sua própria versão (DESING.md seção 2 e 1.2).
 */
public interface StatusMapper {

    /**
     * Traduz o status no vocabulário do cliente para o enum V360.
     *
     * @param rawStatus string de status conforme o cliente envia
     * @return StatusPedido correspondente
     * @throws IllegalArgumentException se o status não for reconhecido
     */
    StatusPedido map(String rawStatus);
}
