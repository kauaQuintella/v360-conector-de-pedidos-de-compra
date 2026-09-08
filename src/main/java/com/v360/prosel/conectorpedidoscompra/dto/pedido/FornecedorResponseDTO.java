package com.v360.prosel.conectorpedidoscompra.dto.pedido;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record FornecedorResponseDTO(
        @JsonProperty("id_fornecedor") UUID idFornecedor,
        @JsonProperty("cnpj") String cnpj,
        @JsonProperty("nome") String nome
) {
}
