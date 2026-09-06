package com.v360.prosel.conectorpedidoscompra.dto.alfa;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * DTO wrapper do payload de entrada do cliente Alfa.
 *
 * O Alfa envia múltiplos pedidos em uma única requisição sob a chave
 * "purchase_orders". Este record representa o objeto raiz do JSON.
 */
public record AlfaPayloadDTO(
        @JsonProperty("purchase_orders")
        @NotEmpty
        @Valid
        List<AlfaPedidoDTO> purchaseOrders
) {}
