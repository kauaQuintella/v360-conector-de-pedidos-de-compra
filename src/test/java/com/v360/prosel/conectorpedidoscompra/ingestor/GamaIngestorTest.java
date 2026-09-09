package com.v360.prosel.conectorpedidoscompra.ingestor;

import com.v360.prosel.conectorpedidoscompra.dto.gama.GamaItemLinhaDTO;
import com.v360.prosel.conectorpedidoscompra.dto.gama.GamaPedidoDTO;
import com.v360.prosel.conectorpedidoscompra.entity.Item;
import com.v360.prosel.conectorpedidoscompra.entity.Pedido;
import com.v360.prosel.conectorpedidoscompra.enums.StatusPedido;
import com.v360.prosel.conectorpedidoscompra.normalizer.GamaDateParser;
import com.v360.prosel.conectorpedidoscompra.normalizer.GamaStatusMapper;
import com.v360.prosel.conectorpedidoscompra.parser.GamaPayloadAgrupador;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GamaIngestorTest {

    private GamaIngestor gamaIngestor;

    @BeforeEach
    void setUp() {
        gamaIngestor = new GamaIngestor(new GamaStatusMapper());
    }

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

    private List<GamaPedidoDTO> payloadOficial() {
        return GamaPayloadAgrupador.agrupar(List.of(
                linha("GL-778", 1, "34567890000112", "Transportes Ideal ME", 1786752000L,
                        "TRP-01", "Pallet de madeira", "CX", 12, "10", "2", 120000L, 1),
                linha("GL-778", 2, "34567890000112", "Transportes Ideal ME", 1786752000L,
                        "TRP-09", "Caixa organizadora", "CX", 3, "4", "0", 10000L, 1),
                linha("GL-779", 1, "56789012000134", "Armazéns Rio Claro Ltda", 1784160000L,
                        "ARM-10", "Estrado metálico", "UN", 1, "100", "100", 3500L, 2)
        ));
    }

    @Test
    @DisplayName("GL-778: cabeçalho OPEN, BRL, GAMA e data epoch")
    void toEntity_gl778_mapeiaCabecalho() {
        Pedido pedido = gamaIngestor.toEntity(payloadOficial().get(0));

        assertThat(pedido.getNumeroPedidoOrigem()).isEqualTo("GL-778");
        assertThat(pedido.getClienteOrigem()).isEqualTo("GAMA");
        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.OPEN);
        assertThat(pedido.getMoeda()).isEqualTo("BRL");
        assertThat(pedido.getDataCriacao()).isEqualTo(GamaDateParser.parse(1786752000L));
        assertThat(pedido.getFornecedor().getCnpj()).isEqualTo("34567890000112");
        assertThat(pedido.getFornecedor().getNome()).isEqualTo("Transportes Ideal ME");
        assertThat(pedido.getItens()).hasSize(2);
        assertThat(pedido.getDataIngestao()).isNull();
    }

    @Test
    @DisplayName("Teste dourado TRP-01: 120 / 24 / 100.00 / UN")
    void toEntity_trp01_converteUnidadeEPreco() {
        Item item = gamaIngestor.toEntity(payloadOficial().get(0)).getItens().get(0);

        assertThat(item.getLinha()).isEqualTo("1");
        assertThat(item.getCodigoMaterial()).isEqualTo("TRP-01");
        assertThat(item.getUnidadeMedida()).isEqualTo("UN");
        assertThat(item.getQuantidadePedida()).isEqualByComparingTo(new BigDecimal("120"));
        assertThat(item.getQuantidadeRecebida()).isEqualByComparingTo(new BigDecimal("24"));
        assertThat(item.getPrecoUnitario()).isEqualByComparingTo(new BigDecimal("100.0000"));
        assertThat(item.getQuantidadePendente()).isNull();
    }

    @Test
    @DisplayName("TRP-09: preço 33.3333 e quantidade recebida convertida (0×3)")
    void toEntity_trp09_dizimaERecebidaConvertida() {
        Item item = gamaIngestor.toEntity(payloadOficial().get(0)).getItens().get(1);

        assertThat(item.getCodigoMaterial()).isEqualTo("TRP-09");
        assertThat(item.getQuantidadePedida()).isEqualByComparingTo(new BigDecimal("12"));
        assertThat(item.getQuantidadeRecebida()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(item.getPrecoUnitario()).isEqualByComparingTo(new BigDecimal("33.3333"));
        assertThat(item.getUnidadeMedida()).isEqualTo("UN");
    }

    @Test
    @DisplayName("GL-779: CLOSED, pedida=recebida 100 — pendência zero no domínio")
    void toEntity_gl779_closedSemPendencia() {
        Pedido pedido = gamaIngestor.toEntity(payloadOficial().get(1));
        Item item = pedido.getItens().get(0);

        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.CLOSED);
        assertThat(item.getQuantidadePedida()).isEqualByComparingTo(new BigDecimal("100"));
        assertThat(item.getQuantidadeRecebida()).isEqualByComparingTo(new BigDecimal("100"));
        assertThat(item.getUnidadeMedida()).isEqualTo("UN");
    }

    @Test
    @DisplayName("Nome e descrição com quebra de linha — sanitiza")
    void toEntity_nomeComQuebra_sanitiza() {
        GamaPedidoDTO dto = GamaPayloadAgrupador.agrupar(List.of(
                linha("GL-1", 1, "12.345.678/0001-90", "Transportes\nIdeal", 1786752000L,
                        "X", "Pallet\nde madeira", "CX", 1, "1", "0", 100L, 1)
        )).get(0);

        Pedido pedido = gamaIngestor.toEntity(dto);

        assertThat(pedido.getFornecedor().getCnpj()).isEqualTo("12345678000190");
        assertThat(pedido.getFornecedor().getNome()).isEqualTo("Transportes Ideal");
        assertThat(pedido.getItens().get(0).getDescricao()).isEqualTo("Pallet de madeira");
    }
}
