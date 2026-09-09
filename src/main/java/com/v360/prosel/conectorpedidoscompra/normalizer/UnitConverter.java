package com.v360.prosel.conectorpedidoscompra.normalizer;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Converte quantidade e preço da unidade de compra do Gama para a unidade da nota,
 * via {@code fator_conv}. Escala 4 no preço alinha a {@code NUMERIC(15,4)}.
 */
public final class UnitConverter {

    private static final int ESCALA_INTERMEDIARIA = 10;
    private static final int ESCALA_PRECO = 4;

    private UnitConverter() {
    }

    public static BigDecimal converterQuantidade(BigDecimal quantidadeOrigem, int fatorConv) {
        if (quantidadeOrigem == null) {
            return null;
        }
        return quantidadeOrigem.multiply(BigDecimal.valueOf(fatorConv));
    }

    public static BigDecimal converterPrecoUnitario(long precoUnitCentavos, int fatorConv) {
        return BigDecimal.valueOf(precoUnitCentavos)
                .divide(BigDecimal.valueOf(100), ESCALA_INTERMEDIARIA, RoundingMode.HALF_UP)
                .divide(BigDecimal.valueOf(fatorConv), ESCALA_PRECO, RoundingMode.HALF_UP);
    }
}
