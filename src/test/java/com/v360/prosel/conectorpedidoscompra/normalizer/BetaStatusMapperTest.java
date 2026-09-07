package com.v360.prosel.conectorpedidoscompra.normalizer;

import com.v360.prosel.conectorpedidoscompra.enums.StatusPedido;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testes unitários de {@link BetaStatusMapper}.
 *
 * Cobre o vocabulário pt-BR do CSV Beta conforme DIA2 do plano:
 * EM ABERTO → OPEN, BLOQUEADO → BLOCKED, ENCERRADO → CLOSED.
 */
class BetaStatusMapperTest {

    private BetaStatusMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new BetaStatusMapper();
    }

    @Test
    @DisplayName("'EM ABERTO' → OPEN")
    void map_emAberto_retornaOpen() {
        assertThat(mapper.map("EM ABERTO")).isEqualTo(StatusPedido.OPEN);
    }

    @Test
    @DisplayName("'BLOQUEADO' → BLOCKED")
    void map_bloqueado_retornaBlocked() {
        assertThat(mapper.map("BLOQUEADO")).isEqualTo(StatusPedido.BLOCKED);
    }

    @Test
    @DisplayName("'ENCERRADO' → CLOSED")
    void map_encerrado_retornaClosed() {
        assertThat(mapper.map("ENCERRADO")).isEqualTo(StatusPedido.CLOSED);
    }

    @Test
    @DisplayName("Status em minúsculo é normalizado antes do switch")
    void map_minusculo_normalizado() {
        assertThat(mapper.map("em aberto")).isEqualTo(StatusPedido.OPEN);
        assertThat(mapper.map("bloqueado")).isEqualTo(StatusPedido.BLOCKED);
        assertThat(mapper.map("encerrado")).isEqualTo(StatusPedido.CLOSED);
    }

    @Test
    @DisplayName("Status com espaços extras é normalizado (trim)")
    void map_comEspacos_normalizado() {
        assertThat(mapper.map("  EM ABERTO  ")).isEqualTo(StatusPedido.OPEN);
    }

    @Test
    @DisplayName("Status nulo lança IllegalArgumentException")
    void map_null_lancaExcecao() {
        assertThatThrownBy(() -> mapper.map(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Status desconhecido lança IllegalArgumentException com o valor recebido")
    void map_statusDesconhecido_lancaExcecaoComMensagem() {
        assertThatThrownBy(() -> mapper.map("PENDENTE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PENDENTE");
    }

    @Test
    @DisplayName("Status vazio lança IllegalArgumentException")
    void map_statusVazio_lancaExcecao() {
        assertThatThrownBy(() -> mapper.map(""))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
