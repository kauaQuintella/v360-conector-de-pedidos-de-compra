package com.v360.prosel.conectorpedidoscompra.normalizer;

import com.v360.prosel.conectorpedidoscompra.enums.StatusPedido;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testes unitários puros do AlfaStatusMapper.
 * Sem Spring, sem banco — instanciado diretamente com new.
 */
class AlfaStatusMapperTest {

    private AlfaStatusMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new AlfaStatusMapper();
    }

    @Test
    @DisplayName("Status 'OPEN' em maiúsculo — mapeia para StatusPedido.OPEN")
    void map_statusOpen_retornaEnumOpen() {
        assertThat(mapper.map("OPEN")).isEqualTo(StatusPedido.OPEN);
    }

    @Test
    @DisplayName("Status 'CLOSED' em maiúsculo — mapeia para StatusPedido.CLOSED")
    void map_statusClosed_retornaEnumClosed() {
        assertThat(mapper.map("CLOSED")).isEqualTo(StatusPedido.CLOSED);
    }

    @Test
    @DisplayName("Status 'BLOCKED' em maiúsculo — mapeia para StatusPedido.BLOCKED")
    void map_statusBlocked_retornaEnumBlocked() {
        assertThat(mapper.map("BLOCKED")).isEqualTo(StatusPedido.BLOCKED);
    }

    @Test
    @DisplayName("Status em minúsculo — mapeamento é case-insensitive")
    void map_statusLowercase_mapeiaInsensitivo() {
        assertThat(mapper.map("open")).isEqualTo(StatusPedido.OPEN);
    }

    @Test
    @DisplayName("Status em mixed case — mapeamento é case-insensitive")
    void map_statusMixedCase_mapeiaInsensitivo() {
        assertThat(mapper.map("Open")).isEqualTo(StatusPedido.OPEN);
    }

    @Test
    @DisplayName("Status com espaços nas bordas — mapeia após trim")
    void map_statusComEspacos_mapeiaAposTrim() {
        assertThat(mapper.map(" OPEN ")).isEqualTo(StatusPedido.OPEN);
    }

    @Test
    @DisplayName("Status desconhecido — lança IllegalArgumentException com o valor recebido na mensagem")
    void map_statusDesconhecido_lancaIllegalArgumentException() {
        assertThatThrownBy(() -> mapper.map("PENDENTE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PENDENTE");
    }

    @Test
    @DisplayName("Status nulo — lança IllegalArgumentException")
    void map_statusNulo_lancaIllegalArgumentException() {
        assertThatThrownBy(() -> mapper.map(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
