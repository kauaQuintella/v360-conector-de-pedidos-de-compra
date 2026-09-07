package com.v360.prosel.conectorpedidoscompra.dto.beta;

import java.util.List;

/**
 * Pedido Beta após join cabeçalho+itens. Campos ainda brutos (strings do CSV);
 * conversão numérica/data/status é responsabilidade do {@code BetaIngestor}.
 */
public record BetaPedidoDTO(
        String numeroPedido,
        String fornecedorCnpj,
        String fornecedorRazaoSocial,
        String emissao,
        String situacao,
        String moeda,
        List<BetaItemDTO> itens
) {}
