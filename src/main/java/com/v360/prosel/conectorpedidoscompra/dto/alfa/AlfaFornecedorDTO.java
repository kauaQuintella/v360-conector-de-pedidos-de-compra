package com.v360.prosel.conectorpedidoscompra.dto.alfa;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * DTO de entrada do Alfa para o bloco de fornecedor.
 * O CNPJ é recebido aqui possivelmente com máscara — o AlfaIngestor aplica
 * CnpjSanitizer antes de persistir (DESING.md seção 2).
 */
public record AlfaFornecedorDTO(
        @JsonProperty("tax_id")
        @NotBlank String cnpj,

        @JsonProperty("name")
        @NotBlank String nome
) {}
