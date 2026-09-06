package com.v360.prosel.conectorpedidoscompra.dto.alfa;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

/**
 * DTO raiz de entrada do Alfa para um pedido de compra completo.
 *
 * O Alfa envia dataCriacao no formato YYYY-MM-DD (ex: "2026-08-05").
 * Desserializado como LocalDate; a conversão para Instant é responsabilidade
 * do AlfaDateParser (normalizador).
 */
public record AlfaPedidoDTO(
        @JsonProperty("po_number")
        @NotBlank
        String numeroPedido,

        @JsonProperty("created_at")
        @NotNull
        LocalDate dataCriacao,

        @NotBlank
        String status,

        @JsonProperty("currency")
        @NotBlank
        String moeda,

        @JsonProperty("vendor")
        @NotNull
        @Valid
        AlfaFornecedorDTO fornecedor,

        @JsonProperty("items")
        @NotEmpty
        @Valid
        List<AlfaItemDTO> itens
) {}
