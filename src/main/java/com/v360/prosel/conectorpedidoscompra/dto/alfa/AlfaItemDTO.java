package com.v360.prosel.conectorpedidoscompra.dto.alfa;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * DTO de entrada do Alfa para cada linha de item do pedido.
 * Todos os campos numéricos usam BigDecimal para preservar precisão (DESING.md 3.3).
 */
public record AlfaItemDTO(
        @NotBlank
        String linha,

        @JsonProperty("codigo_material")
        @NotBlank
        String codigoMaterial,

        String descricao,

        @JsonProperty("unidade_medida")
        String unidadeMedida,

        @JsonProperty("quantidade_pedida")
        @NotNull
        BigDecimal quantidadePedida,

        @JsonProperty("quantidade_recebida")
        @NotNull
        BigDecimal quantidadeRecebida,

        @JsonProperty("preco_unitario")
        @NotNull
        BigDecimal precoUnitario
) {}
