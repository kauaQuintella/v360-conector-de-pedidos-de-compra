package com.v360.prosel.conectorpedidoscompra.dto.gama;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/**
 * Uma linha do JSON achatado do Gama Logística (1 objeto = 1 item).
 * Espelha {@code public/gamaLogisticaPayload.json}. Agrupamento por {@code ped}
 * é responsabilidade do {@code GamaPayloadAgrupador}.
 */
public record GamaItemLinhaDTO(
        @JsonProperty("ped")
        @NotBlank
        String ped,

        @JsonProperty("item")
        @NotNull
        Integer item,

        @JsonProperty("cnpj_fornecedor")
        @NotBlank
        String cnpjFornecedor,

        @JsonProperty("nome_fornecedor")
        @NotBlank
        String nomeFornecedor,

        @JsonProperty("dt_criacao")
        @NotNull
        Long dtCriacao,

        @JsonProperty("cod_mat")
        @NotBlank
        String codMat,

        @JsonProperty("desc_mat")
        @NotBlank
        String descMat,

        @JsonProperty("um")
        @NotBlank
        String um,

        @JsonProperty("fator_conv")
        @NotNull
        @Positive
        Integer fatorConv,

        @JsonProperty("qtd_ped")
        @NotNull
        @Positive
        BigDecimal qtdPed,

        @JsonProperty("qtd_rec")
        @NotNull
        @PositiveOrZero
        BigDecimal qtdRec,

        @JsonProperty("preco_unit_centavos")
        @NotNull
        @PositiveOrZero
        Long precoUnitCentavos,

        @JsonProperty("situacao")
        @NotNull
        Integer situacao
) {}
