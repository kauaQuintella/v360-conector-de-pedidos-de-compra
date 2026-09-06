package com.v360.prosel.conectorpedidoscompra.normalizer;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Utilitário puro (sem estado, sem Spring) para normalização de data do cliente Alfa.
 *
 * O Alfa envia datas no formato YYYY-MM-DD, sem informação de hora ou fuso horário.
 * Este normalizador converte LocalDate → Instant aplicando meia-noite (00:00:00)
 * no fuso padrão America/Sao_Paulo, alinhado com o contrato interno V360
 * que armazena datas como TIMESTAMPTZ (DESING.md seção 2 e 3).
 */
public class AlfaDateParser {

    static final ZoneId FUSO_PADRAO = ZoneId.of("America/Sao_Paulo");

    private AlfaDateParser() {
        // utilitário estático — instanciação desnecessária
    }

    /**
     * Converte uma data sem hora (LocalDate) para um instante UTC,
     * aplicando meia-noite no fuso America/Sao_Paulo.
     *
     * Ex.: "2026-08-05" → 2026-08-05T03:00:00Z (UTC)
     *
     * @param data data bruta recebida do Alfa
     * @return Instant correspondente ao início do dia no fuso padrão, ou null se a entrada for null
     */
    public static Instant parse(LocalDate data) {
        if (data == null) return null;
        return data.atStartOfDay(FUSO_PADRAO).toInstant();
    }
}
