package com.v360.prosel.conectorpedidoscompra.normalizer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class GamaDateParserTest {

    @Test
    @DisplayName("Epoch do payload oficial GL-778 — 2026-08-15T00:00:00Z")
    void parse_epochGl778_retornaQuinzeDeAgosto() {
        assertThat(GamaDateParser.parse(1786752000L))
                .isEqualTo(Instant.parse("2026-08-15T00:00:00Z"));
    }

    @Test
    @DisplayName("Epoch nulo — retorna null")
    void parse_nulo_retornaNull() {
        assertThat(GamaDateParser.parse(null)).isNull();
    }
}
