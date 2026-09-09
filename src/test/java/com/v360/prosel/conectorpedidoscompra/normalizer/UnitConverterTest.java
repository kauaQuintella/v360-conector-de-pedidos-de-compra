package com.v360.prosel.conectorpedidoscompra.normalizer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class UnitConverterTest {

    @Test
    @DisplayName("Quantidade TRP-01: 10×12=120 e 2×12=24")
    void converterQuantidade_trp01_multiplicaFator() {
        assertThat(UnitConverter.converterQuantidade(new BigDecimal("10"), 12))
                .isEqualByComparingTo(new BigDecimal("120"));
        assertThat(UnitConverter.converterQuantidade(new BigDecimal("2"), 12))
                .isEqualByComparingTo(new BigDecimal("24"));
    }

    @Test
    @DisplayName("Preço TRP-01: 120000 centavos / 12 = 100.0000")
    void converterPrecoUnitario_trp01_cemExatos() {
        assertThat(UnitConverter.converterPrecoUnitario(120000L, 12))
                .isEqualByComparingTo(new BigDecimal("100.0000"));
    }

    @Test
    @DisplayName("Preço TRP-09: 10000 centavos / 3 = 33.3333")
    void converterPrecoUnitario_trp09_dizimaQuatroCasas() {
        assertThat(UnitConverter.converterPrecoUnitario(10000L, 3))
                .isEqualByComparingTo(new BigDecimal("33.3333"));
    }

    @Test
    @DisplayName("Quantidade nula — retorna null")
    void converterQuantidade_nula_retornaNull() {
        assertThat(UnitConverter.converterQuantidade(null, 12)).isNull();
    }
}
