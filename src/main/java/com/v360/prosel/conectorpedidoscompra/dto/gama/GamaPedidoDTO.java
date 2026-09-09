package com.v360.prosel.conectorpedidoscompra.dto.gama;

import java.util.List;

/**
 * Pedido Gama após agrupamento das linhas por {@code ped}.
 * Não é o body HTTP — o payload oficial é um array de {@link GamaItemLinhaDTO}.
 */
public record GamaPedidoDTO(
        String ped,
        String cnpjFornecedor,
        String nomeFornecedor,
        Long dtCriacao,
        Integer situacao,
        List<GamaItemLinhaDTO> itens
) {}
