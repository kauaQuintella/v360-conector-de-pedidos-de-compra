package com.v360.prosel.conectorpedidoscompra.normalizer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testes unitários de {@link BetaNumberParser}.
 *
 * Cobre: número com separador de milhar (1.200,000), número simples (6,49),
 * zero, entrada inválida, null e vazio.
 */
class BetaNumberParserTest {

    @Test
    @DisplayName("Número BR com milhar e decimal: 1.200,000 → 1200.000")
    void parse_comMilharEDecimal_retornaBigDecimalCorreto() {
        BigDecimal resultado = BetaNumberParser.parse("1.200,000");

        assertThat(resultado).isEqualByComparingTo(new BigDecimal("1200.000"));
    }

    @Test
    @DisplayName("Número BR simples sem milhar: 6,49 → 6.49")
    void parse_semMilharComDecimal_retornaBigDecimalCorreto() {
        BigDecimal resultado = BetaNumberParser.parse("6,49");

        assertThat(resultado).isEqualByComparingTo(new BigDecimal("6.49"));
    }

    @Test
    @DisplayName("Número BR: 4,15 → 4.15")
    void parse_quatroVirgulaDezeSeis_retornaBigDecimalCorreto() {
        BigDecimal resultado = BetaNumberParser.parse("4,15");

        assertThat(resultado).isEqualByComparingTo(new BigDecimal("4.15"));
    }

    @Test
    @DisplayName("Número BR: 27,90 → 27.90")
    void parse_vinteSeteVirgulaNoventa_retornaBigDecimalCorreto() {
        BigDecimal resultado = BetaNumberParser.parse("27,90");

        assertThat(resultado).isEqualByComparingTo(new BigDecimal("27.90"));
    }

    @Test
    @DisplayName("Quantidade zero: 0,000 → 0")
    void parse_zero_retornaZero() {
        BigDecimal resultado = BetaNumberParser.parse("0,000");

        assertThat(resultado).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Quantidade grande com milhar: 2.000,000 → 2000.000")
    void parse_doisMilComDecimal_retornaBigDecimalCorreto() {
        BigDecimal resultado = BetaNumberParser.parse("2.000,000");

        assertThat(resultado).isEqualByComparingTo(new BigDecimal("2000.000"));
    }

    @Test
    @DisplayName("Entrada com espaços é normalizada antes de converter")
    void parse_comEspacos_retornaBigDecimalCorreto() {
        BigDecimal resultado = BetaNumberParser.parse("  1,50  ");

        assertThat(resultado).isEqualByComparingTo(new BigDecimal("1.50"));
    }

    @Test
    @DisplayName("Entrada null retorna null")
    void parse_null_retornaNull() {
        assertThat(BetaNumberParser.parse(null)).isNull();
    }

    @Test
    @DisplayName("Entrada vazia lança IllegalArgumentException")
    void parse_entradaVazia_lancaExcecao() {
        assertThatThrownBy(() -> BetaNumberParser.parse(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Entrada com texto não numérico lança IllegalArgumentException")
    void parse_textoNaoNumerico_lancaExcecao() {
        assertThatThrownBy(() -> BetaNumberParser.parse("ABC"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ABC");
    }
}
