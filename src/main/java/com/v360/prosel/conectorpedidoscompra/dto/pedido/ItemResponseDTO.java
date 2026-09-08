package com.v360.prosel.conectorpedidoscompra.dto.pedido;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.UUID;

public record ItemResponseDTO(
        @JsonProperty("id_item") UUID idItem,
        @JsonProperty("linha") String linha,
        @JsonProperty("codigo_material") String codigoMaterial,
        @JsonProperty("descricao") String descricao,
        @JsonProperty("unidade_medida") String unidadeMedida,
        @JsonProperty("quantidade_pedida") BigDecimal quantidadePedida,
        @JsonProperty("quantidade_recebida") BigDecimal quantidadeRecebida,
        @JsonProperty("quantidade_pendente") BigDecimal quantidadePendente,
        @JsonProperty("preco_unitario") BigDecimal precoUnitario
) {
}
