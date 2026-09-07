package com.v360.prosel.conectorpedidoscompra.parser;

import com.v360.prosel.conectorpedidoscompra.dto.beta.BetaCabecalhoLinhaDTO;
import com.v360.prosel.conectorpedidoscompra.dto.beta.BetaItemLinhaDTO;
import com.v360.prosel.conectorpedidoscompra.dto.beta.BetaPedidoDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testes unitários de {@link BetaCsvParser}.
 *
 * Sem Spring, sem banco. Cobre: payload oficial (os dois CSVs reais),
 * join com erros (item sem cabeçalho, cabeçalho sem item).
 */
class BetaCsvParserTest {

    private BetaCsvParser parser;

    // Reproduz exatamente o conteúdo dos arquivos em public/betaAlimentos/
    private static final String CABECALHO_OFICIAL =
            "NUMERO_PEDIDO;FORNECEDOR_CNPJ;FORNECEDOR_RAZAO_SOCIAL;EMISSAO;SITUACAO;MOEDA\n"
            + "20260088412;12.345.678/0001-90;Distribuidora Horizonte Ltda;15/08/2026;EM\n"
            + "ABERTO;BRL\n"
            + "20260088413;98.765.432/0001-55;Frigorífico Boa Mesa\n"
            + "S.A.;01/08/2026;BLOQUEADO;BRL\n";

    private static final String ITENS_OFICIAL =
            "NUMERO_PEDIDO;ITEM;CODIGO_MATERIAL;DESCRICAO;UNIDADE;QTD_PEDIDA;QTD_RECEBIDA;PR\n"
            + "ECO_UNITARIO\n"
            + "20260088412;1;MAT-77;Óleo de soja 900ml;UN;1.200,000;400,000;6,49\n"
            + "20260088412;2;MAT-78;Açúcar refinado 1kg;UN;500,000;0,000;4,15\n"
            + "20260088413;1;MAT-91;Carne bovina dianteiro kg;KG;2.000,000;0,000;27,90\n";

    @BeforeEach
    void setUp() {
        parser = new BetaCsvParser();
    }

    // =========================================================================
    // Payload oficial completo
    // =========================================================================

    @Test
    @DisplayName("Payload oficial: dois pedidos com número correto e dados do cabeçalho")
    void parse_payloadOficial_doisPedidos() {
        List<BetaPedidoDTO> pedidos = parser.parse(CABECALHO_OFICIAL, ITENS_OFICIAL);

        assertThat(pedidos).hasSize(2);
    }

    @Test
    @DisplayName("Pedido 20260088412: cabeçalho mapeado corretamente")
    void parse_pedido412_cabecalhoCorreto() {
        BetaPedidoDTO pedido = parser.parse(CABECALHO_OFICIAL, ITENS_OFICIAL).get(0);

        assertThat(pedido.numeroPedido()).isEqualTo("20260088412");
        assertThat(pedido.fornecedorCnpj()).isEqualTo("12.345.678/0001-90");
        assertThat(pedido.fornecedorRazaoSocial()).isEqualTo("Distribuidora Horizonte Ltda");
        assertThat(pedido.emissao()).isEqualTo("15/08/2026");
        assertThat(pedido.situacao()).isEqualTo("EM ABERTO");
        assertThat(pedido.moeda()).isEqualTo("BRL");
    }

    @Test
    @DisplayName("Pedido 20260088412: dois itens com campos corretos")
    void parse_pedido412_doisItensCorretos() {
        BetaPedidoDTO pedido = parser.parse(CABECALHO_OFICIAL, ITENS_OFICIAL).get(0);

        assertThat(pedido.itens()).hasSize(2);

        var item1 = pedido.itens().get(0);
        assertThat(item1.linha()).isEqualTo("1");
        assertThat(item1.codigoMaterial()).isEqualTo("MAT-77");
        assertThat(item1.unidade()).isEqualTo("UN");
        assertThat(item1.qtdPedida()).isEqualTo("1.200,000");
        assertThat(item1.qtdRecebida()).isEqualTo("400,000");
        assertThat(item1.precoUnitario()).isEqualTo("6,49");

        var item2 = pedido.itens().get(1);
        assertThat(item2.linha()).isEqualTo("2");
        assertThat(item2.codigoMaterial()).isEqualTo("MAT-78");
        assertThat(item2.qtdPedida()).isEqualTo("500,000");
        assertThat(item2.precoUnitario()).isEqualTo("4,15");
    }

    @Test
    @DisplayName("Pedido 20260088413: razão social com S.A. remontada e status BLOQUEADO")
    void parse_pedido413_razaoSocialEStatusCorretos() {
        BetaPedidoDTO pedido = parser.parse(CABECALHO_OFICIAL, ITENS_OFICIAL).get(1);

        assertThat(pedido.numeroPedido()).isEqualTo("20260088413");
        assertThat(pedido.fornecedorRazaoSocial()).isEqualTo("Frigorífico Boa Mesa S.A.");
        assertThat(pedido.situacao()).isEqualTo("BLOQUEADO");
        assertThat(pedido.emissao()).isEqualTo("01/08/2026");
    }

    @Test
    @DisplayName("Pedido 20260088413: um item KG com campos corretos")
    void parse_pedido413_umItemKg() {
        BetaPedidoDTO pedido = parser.parse(CABECALHO_OFICIAL, ITENS_OFICIAL).get(1);

        assertThat(pedido.itens()).hasSize(1);
        var item = pedido.itens().get(0);
        assertThat(item.codigoMaterial()).isEqualTo("MAT-91");
        assertThat(item.unidade()).isEqualTo("KG");
        assertThat(item.qtdPedida()).isEqualTo("2.000,000");
        assertThat(item.qtdRecebida()).isEqualTo("0,000");
        assertThat(item.precoUnitario()).isEqualTo("27,90");
    }

    // =========================================================================
    // Join — erros
    // =========================================================================

    @Test
    @DisplayName("Item sem cabeçalho correspondente lança IllegalArgumentException")
    void join_itemSemCabecalho_lancaExcecao() {
        BetaItemLinhaDTO item = new BetaItemLinhaDTO();
        item.setNumeroPedido("99999");
        item.setItem("1");
        item.setCodigoMaterial("MAT-X");
        item.setDescricao("desc");
        item.setUnidade("UN");
        item.setQtdPedida("1,000");
        item.setQtdRecebida("0,000");
        item.setPrecoUnitario("1,00");

        BetaCabecalhoLinhaDTO cabecalho = new BetaCabecalhoLinhaDTO();
        cabecalho.setNumeroPedido("11111");
        cabecalho.setFornecedorCnpj("12345678000190");
        cabecalho.setFornecedorRazaoSocial("Fornecedor X");
        cabecalho.setEmissao("01/01/2026");
        cabecalho.setSituacao("EM ABERTO");
        cabecalho.setMoeda("BRL");

        assertThatThrownBy(() -> parser.join(List.of(cabecalho), List.of(item)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("99999");
    }

    @Test
    @DisplayName("Cabeçalho sem itens lança IllegalArgumentException")
    void join_cabecalhoSemItens_lancaExcecao() {
        BetaCabecalhoLinhaDTO cabecalho = new BetaCabecalhoLinhaDTO();
        cabecalho.setNumeroPedido("20260099001");
        cabecalho.setFornecedorCnpj("12345678000190");
        cabecalho.setFornecedorRazaoSocial("Sem Itens Ltda");
        cabecalho.setEmissao("01/01/2026");
        cabecalho.setSituacao("EM ABERTO");
        cabecalho.setMoeda("BRL");

        assertThatThrownBy(() -> parser.join(List.of(cabecalho), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("20260099001");
    }
}
