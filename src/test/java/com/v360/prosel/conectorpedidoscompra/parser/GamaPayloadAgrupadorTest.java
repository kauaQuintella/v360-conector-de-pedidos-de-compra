package com.v360.prosel.conectorpedidoscompra.parser;

import com.v360.prosel.conectorpedidoscompra.dto.gama.GamaItemLinhaDTO;
import com.v360.prosel.conectorpedidoscompra.dto.gama.GamaPedidoDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GamaPayloadAgrupadorTest {

    private GamaItemLinhaDTO linha(
            String ped, int item, String cnpj, String nome, long dtCriacao,
            String codMat, String descMat, String um, int fator,
            String qtdPed, String qtdRec, long centavos, int situacao
    ) {
        return new GamaItemLinhaDTO(
                ped, item, cnpj, nome, dtCriacao, codMat, descMat, um, fator,
                new BigDecimal(qtdPed), new BigDecimal(qtdRec), centavos, situacao
        );
    }

    private GamaItemLinhaDTO trp01() {
        return linha("GL-778", 1, "34567890000112", "Transportes Ideal ME", 1786752000L,
                "TRP-01", "Pallet de madeira", "CX", 12, "10", "2", 120000L, 1);
    }

    private GamaItemLinhaDTO trp09() {
        return linha("GL-778", 2, "34567890000112", "Transportes Ideal ME", 1786752000L,
                "TRP-09", "Caixa organizadora", "CX", 3, "4", "0", 10000L, 1);
    }

    private GamaItemLinhaDTO arm10() {
        return linha("GL-779", 1, "56789012000134", "Armazéns Rio Claro Ltda", 1784160000L,
                "ARM-10", "Estrado metálico", "UN", 1, "100", "100", 3500L, 2);
    }

    @Test
    @DisplayName("Payload oficial: 2 pedidos (GL-778 com 2 itens, GL-779 com 1)")
    void agrupar_payloadOficial_doisPedidosNaOrdem() {
        List<GamaPedidoDTO> pedidos = GamaPayloadAgrupador.agrupar(List.of(trp01(), trp09(), arm10()));

        assertThat(pedidos).hasSize(2);
        assertThat(pedidos.get(0).ped()).isEqualTo("GL-778");
        assertThat(pedidos.get(0).itens()).hasSize(2);
        assertThat(pedidos.get(1).ped()).isEqualTo("GL-779");
        assertThat(pedidos.get(1).itens()).hasSize(1);
        assertThat(pedidos.get(1).situacao()).isEqualTo(2);
    }

    @Test
    @DisplayName("Cabeçalho divergente no mesmo ped — lança IllegalArgumentException")
    void agrupar_cnpjDivergente_lancaExcecao() {
        GamaItemLinhaDTO divergente = linha("GL-778", 2, "00000000000000", "Transportes Ideal ME",
                1786752000L, "TRP-09", "Caixa organizadora", "CX", 3, "4", "0", 10000L, 1);

        assertThatThrownBy(() -> GamaPayloadAgrupador.agrupar(List.of(trp01(), divergente)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("GL-778");
    }

    @Test
    @DisplayName("Lista vazia — lança IllegalArgumentException")
    void agrupar_listaVazia_lancaExcecao() {
        assertThatThrownBy(() -> GamaPayloadAgrupador.agrupar(Collections.emptyList()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vazio");
    }

    @Test
    @DisplayName("Lista nula — lança IllegalArgumentException")
    void agrupar_listaNula_lancaExcecao() {
        assertThatThrownBy(() -> GamaPayloadAgrupador.agrupar(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
