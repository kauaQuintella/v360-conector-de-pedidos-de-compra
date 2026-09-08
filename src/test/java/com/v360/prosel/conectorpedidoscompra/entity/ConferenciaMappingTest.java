package com.v360.prosel.conectorpedidoscompra.entity;

import com.v360.prosel.conectorpedidoscompra.enums.ResultadoConferencia;
import com.v360.prosel.conectorpedidoscompra.enums.TipoDivergencia;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConferenciaMappingTest {

    @Test
    void adicionarDivergencia_mantemRelacionamentoBidirecional() {
        Conferencia conferencia = new Conferencia();
        conferencia.setResultado(ResultadoConferencia.REJEITADA);

        Divergencia divergencia = new Divergencia();
        divergencia.setTipo(TipoDivergencia.MATERIAL_NAO_ENCONTRADO);
        divergencia.setCodigoMaterial("MAT-1");

        conferencia.adicionarDivergencia(divergencia);

        assertThat(conferencia.getDivergencias()).containsExactly(divergencia);
        assertThat(divergencia.getConferencia()).isSameAs(conferencia);
    }

    @Test
    void conferenciaSemPedido_eValidaParaPedidoNaoEncontrado() {
        Conferencia conferencia = new Conferencia();
        conferencia.setResultado(ResultadoConferencia.REJEITADA);

        Divergencia divergencia = new Divergencia();
        divergencia.setTipo(TipoDivergencia.PEDIDO_NAO_ENCONTRADO);
        conferencia.adicionarDivergencia(divergencia);

        assertThat(conferencia.getPedido()).isNull();
        assertThat(divergencia.getConferencia()).isSameAs(conferencia);
    }
}
