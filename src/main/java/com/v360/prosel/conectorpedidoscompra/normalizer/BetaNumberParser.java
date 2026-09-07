package com.v360.prosel.conectorpedidoscompra.normalizer;

import java.math.BigDecimal;

/**
 * Converte números no padrão brasileiro do CSV Beta
 * ({@code 1.200,000}, {@code 6,49}) para {@link BigDecimal}.
 */
public final class BetaNumberParser {

    private BetaNumberParser() {
    }

    /**
     * @param bruto quantidade ou preço como no CSV (milhar {@code .}, decimal {@code ,})
     * @return valor numérico, ou null se a entrada for null
     */
    public static BigDecimal parse(String bruto) {
        if (bruto == null) {
            return null;
        }
        String trimmed = bruto.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Número BR vazio");
        }
        String normalizado = trimmed.replace(".", "").replace(",", ".");
        try {
            return new BigDecimal(normalizado);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Número BR inválido: '" + bruto + "'", e);
        }
    }
}
