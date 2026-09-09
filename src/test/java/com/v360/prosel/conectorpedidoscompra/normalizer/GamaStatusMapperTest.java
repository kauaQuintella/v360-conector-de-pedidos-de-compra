package com.v360.prosel.conectorpedidoscompra.normalizer;

import com.v360.prosel.conectorpedidoscompra.enums.StatusPedido;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GamaStatusMapperTest {

    private GamaStatusMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new GamaStatusMapper();
    }

    @Test
    @DisplayName("Situação 1 — OPEN")
    void map_um_retornaOpen() {
        assertThat(mapper.map(1)).isEqualTo(StatusPedido.OPEN);
    }

    @Test
    @DisplayName("Situação 2 — CLOSED")
    void map_dois_retornaClosed() {
        assertThat(mapper.map(2)).isEqualTo(StatusPedido.CLOSED);
    }

    @Test
    @DisplayName("Situação 3 — BLOCKED")
    void map_tres_retornaBlocked() {
        assertThat(mapper.map(3)).isEqualTo(StatusPedido.BLOCKED);
    }

    @Test
    @DisplayName("Situação desconhecida — IllegalArgumentException")
    void map_desconhecida_lancaExcecao() {
        assertThatThrownBy(() -> mapper.map(4))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("4");
        assertThatThrownBy(() -> mapper.map(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0");
    }

    @Test
    @DisplayName("Situação nula — IllegalArgumentException")
    void map_nula_lancaExcecao() {
        assertThatThrownBy(() -> mapper.map(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
