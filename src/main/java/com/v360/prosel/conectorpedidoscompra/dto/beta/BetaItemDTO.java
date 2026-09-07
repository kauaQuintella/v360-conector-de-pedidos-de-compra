package com.v360.prosel.conectorpedidoscompra.dto.beta;

/**
 * Item de um pedido Beta após o join. Quantidades e preço ainda no formato BR (string).
 */
public record BetaItemDTO(
        String linha,
        String codigoMaterial,
        String descricao,
        String unidade,
        String qtdPedida,
        String qtdRecebida,
        String precoUnitario
) {}
