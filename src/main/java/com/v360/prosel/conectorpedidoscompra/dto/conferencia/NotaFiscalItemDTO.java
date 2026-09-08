package com.v360.prosel.conectorpedidoscompra.dto.conferencia;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class NotaFiscalItemDTO {
    @NotBlank
    @JsonProperty("codigo_material")
    private String codigoMaterial;

    @NotNull
    @DecimalMin("0")
    @JsonProperty("quantidade")
    private BigDecimal quantidade;

    @NotNull
    @DecimalMin("0")
    @JsonProperty("valor_total")
    private BigDecimal valorTotal;

    public NotaFiscalItemDTO(String codigoMaterial, BigDecimal quantidade, BigDecimal valorTotal) {
        this.codigoMaterial = codigoMaterial;
        this.quantidade = quantidade;
        this.valorTotal = valorTotal;
    }
}
