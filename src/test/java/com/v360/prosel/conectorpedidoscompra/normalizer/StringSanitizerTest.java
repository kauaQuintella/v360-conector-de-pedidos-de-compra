package com.v360.prosel.conectorpedidoscompra.normalizer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes unitários puros do StringSanitizer.
 * Sem Spring, sem banco — lógica de transformação de string isolada.
 */
class StringSanitizerTest {

    @Test
    @DisplayName("String com quebra de linha (\\n) — substitui por espaço")
    void sanitize_stringComQuebraLinha_substituiPorEspaco() {
        assertThat(StringSanitizer.sanitize("Metalúrgica São Jorge\nS.A."))
                .isEqualTo("Metalúrgica São Jorge S.A.");
    }

    @Test
    @DisplayName("String com retorno de carro (\\r) — substitui por espaço")
    void sanitize_stringComRetornoCarro_substituiPorEspaco() {
        assertThat(StringSanitizer.sanitize("Empresa\rS.A."))
                .isEqualTo("Empresa S.A.");
    }

    @Test
    @DisplayName("String com tabulação (\\t) — substitui por espaço")
    void sanitize_stringComTabulacao_substituiPorEspaco() {
        assertThat(StringSanitizer.sanitize("Empresa\tS.A."))
                .isEqualTo("Empresa S.A.");
    }

    @Test
    @DisplayName("String com múltiplos caracteres de controle consecutivos — substitui cada um por espaço")
    void sanitize_stringComMultiplosControles_substituiTodos() {
        // Cada caractere de controle é substituído por um espaço, o trim() remove bordas
        assertThat(StringSanitizer.sanitize("Empresa\n\r\tS.A."))
                .isEqualTo("Empresa   S.A.");
    }

    @Test
    @DisplayName("String limpa sem caracteres de controle — retorna o mesmo valor")
    void sanitize_stringLimpa_retornaMesmoValor() {
        assertThat(StringSanitizer.sanitize("Empresa Normal"))
                .isEqualTo("Empresa Normal");
    }

    @Test
    @DisplayName("String nula — retorna null sem lançar exceção")
    void sanitize_stringNula_retornaNull() {
        assertThat(StringSanitizer.sanitize(null)).isNull();
    }

    @Test
    @DisplayName("String com espaços nas bordas — aplica trim()")
    void sanitize_stringComEspacosNasBordas_aplicaTrim() {
        assertThat(StringSanitizer.sanitize("  Empresa  "))
                .isEqualTo("Empresa");
    }
}
