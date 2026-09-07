package com.v360.prosel.conectorpedidoscompra.normalizer;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Data civil {@code dd/MM/yyyy} do Beta → {@link Instant} à meia-noite em
 * {@code America/Sao_Paulo}, o mesmo critério do {@link AlfaDateParser}.
 */
public final class BetaDateParser {

    static final ZoneId FUSO_PADRAO = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter FORMATO_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private BetaDateParser() {
    }

    public static Instant parse(String dataBr) {
        if (dataBr == null) {
            return null;
        }
        String trimmed = dataBr.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Data BR vazia");
        }
        try {
            return parse(LocalDate.parse(trimmed, FORMATO_BR));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Data BR inválida: '" + dataBr + "'", e);
        }
    }

    public static Instant parse(LocalDate data) {
        if (data == null) {
            return null;
        }
        return data.atStartOfDay(FUSO_PADRAO).toInstant();
    }
}
