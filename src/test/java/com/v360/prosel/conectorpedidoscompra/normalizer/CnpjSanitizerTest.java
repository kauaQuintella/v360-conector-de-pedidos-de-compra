package com.v360.prosel.conectorpedidoscompra.normalizer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes unitários puros do CnpjSanitizer.
 * Sem Spring, sem banco — lógica de transformação de string isolada.
 */
class CnpjSanitizerTest {

    @Test
    @DisplayName("CNPJ com máscara completa — remove pontos, barra e traço")
    void sanitize_cnpjComMascara_retornaSomenteDigitos() {
        assertThat(CnpjSanitizer.sanitize("12.345.678/0001-90"))
                .isEqualTo("12345678000190");
    }

    @Test
    @DisplayName("CNPJ sem máscara — retorna o mesmo valor")
    void sanitize_cnpjSemMascara_retornaMesmoValor() {
        assertThat(CnpjSanitizer.sanitize("12345678000190"))
                .isEqualTo("12345678000190");
    }

    @Test
    @DisplayName("CNPJ nulo — retorna null sem lançar exceção")
    void sanitize_cnpjNulo_retornaNull() {
        assertThat(CnpjSanitizer.sanitize(null)).isNull();
    }

    @Test
    @DisplayName("CNPJ com espaços — remove espaços")
    void sanitize_cnpjComEspacos_removeEspacos() {
        assertThat(CnpjSanitizer.sanitize("123 456 780001 90"))
                .isEqualTo("12345678000190");
    }

    @Test
    @DisplayName("CNPJ vazio — retorna string vazia")
    void sanitize_cnpjVazio_retornaStringVazia() {
        assertThat(CnpjSanitizer.sanitize("")).isEqualTo("");
    }
}
