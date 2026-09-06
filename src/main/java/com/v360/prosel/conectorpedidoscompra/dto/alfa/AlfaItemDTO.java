package com.v360.prosel.conectorpedidoscompra.dto.alfa;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * DTO de entrada do Alfa para cada linha de item do pedido.
 * Todos os campos numéricos usam BigDecimal para preservar precisão (DESING.md 3.3).
 * O campo "line" vem como inteiro no JSON e é desserializado como String pelo Jackson.
 */
public record AlfaItemDTO(
        @JsonProperty("line")
        @NotBlank
        String linha,

        @JsonProperty("material")
        @NotBlank
        String codigoMaterial,

        @JsonProperty("description")
        String descricao,

        @JsonProperty("uom")
        String unidadeMedida,

        @JsonProperty("quantity_ordered")
        @NotNull
        BigDecimal quantidadePedida,

        @JsonProperty("quantity_received")
        @NotNull
        BigDecimal quantidadeRecebida,

        @JsonProperty("unit_price")
        @NotNull
        BigDecimal precoUnitario
) {}
