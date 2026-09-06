package com.v360.prosel.conectorpedidoscompra.normalizer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes unitários puros do AlfaDateParser.
 * Sem Spring, sem banco — lógica de conversão LocalDate → Instant isolada.
 */
class AlfaDateParserTest {

    @Test
    @DisplayName("Data válida — retorna Instant correspondente à meia-noite em Sao_Paulo (UTC-3)")
    void parse_dataValida_retornaInstantMeiaNoite() {
        // 2026-08-05 00:00:00 America/Sao_Paulo = 2026-08-05T03:00:00Z (UTC, offset -3)
        LocalDate data = LocalDate.of(2026, 8, 5);

        Instant resultado = AlfaDateParser.parse(data);

        assertThat(resultado).isEqualTo(Instant.parse("2026-08-05T03:00:00Z"));
    }

    @Test
    @DisplayName("Data nula — retorna null sem lançar exceção")
    void parse_dataNula_retornaNull() {
        assertThat(AlfaDateParser.parse(null)).isNull();
    }

    @Test
    @DisplayName("Fuso correto aplicado — ZoneId é America/Sao_Paulo, não UTC fixo")
    void parse_dataValida_fusoCorretoAplicado() {
        LocalDate data = LocalDate.of(2026, 8, 5);
        ZoneId fusoEsperado = ZoneId.of("America/Sao_Paulo");

        Instant resultado = AlfaDateParser.parse(data);

        // O Instant gerado deve ser equivalente ao início do dia no fuso correto
        Instant esperado = data.atStartOfDay(fusoEsperado).toInstant();
        assertThat(resultado).isEqualTo(esperado);
    }
}
