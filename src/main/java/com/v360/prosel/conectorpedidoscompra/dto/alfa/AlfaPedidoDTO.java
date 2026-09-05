package com.v360.prosel.conectorpedidoscompra.dto.alfa;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

/**
 * DTO raiz de entrada do Alfa para um pedido de compra completo.
 *
 * O Alfa envia data em ISO 8601 ("2026-08-15T00:00:00Z") — Jackson desserializa
 * Instant diretamente, sem necessidade de conversão adicional (DESING.md seção 6).
 */
public record AlfaPedidoDTO(
        @JsonProperty("numero_pedido")
        @NotBlank
        String numeroPedido,

        @JsonProperty("data_criacao")
        @NotNull
        Instant dataCriacao,

        @NotBlank
        String status,

        @NotBlank
        String moeda,

        @NotNull
        @Valid
        AlfaFornecedorDTO fornecedor,

        @NotEmpty
        @Valid
        List<AlfaItemDTO> itens
) {}
