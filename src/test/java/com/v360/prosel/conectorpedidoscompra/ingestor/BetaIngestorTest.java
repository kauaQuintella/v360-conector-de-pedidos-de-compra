package com.v360.prosel.conectorpedidoscompra.ingestor;

import com.v360.prosel.conectorpedidoscompra.dto.beta.BetaItemDTO;
import com.v360.prosel.conectorpedidoscompra.dto.beta.BetaPedidoDTO;
import com.v360.prosel.conectorpedidoscompra.entity.Item;
import com.v360.prosel.conectorpedidoscompra.entity.Pedido;
import com.v360.prosel.conectorpedidoscompra.enums.StatusPedido;
import com.v360.prosel.conectorpedidoscompra.normalizer.BetaDateParser;
import com.v360.prosel.conectorpedidoscompra.normalizer.BetaStatusMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Teste unitário puro do BetaIngestor.
 *
 * Sem @SpringBootTest, sem H2, sem banco, sem Mockito.
 * BetaStatusMapper não tem dependências externas — instanciado diretamente com new.
 * PedidoService não é chamado aqui; apenas a transformação DTO → entidade é testada.
 */
class BetaIngestorTest {

    private BetaIngestor betaIngestor;

    @BeforeEach
    void setUp() {
        betaIngestor = new BetaIngestor(new BetaStatusMapper());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private BetaItemDTO item(String linha, String codigo, String descricao,
                              String unidade, String qtdPedida,
                              String qtdRecebida, String preco) {
        return new BetaItemDTO(linha, codigo, descricao, unidade, qtdPedida, qtdRecebida, preco);
    }

    private BetaItemDTO itemPadrao() {
        return item("1", "MAT-77", "Óleo de soja 900ml", "UN",
                "1.200,000", "400,000", "6,49");
    }

    private BetaPedidoDTO pedido(String numero, String cnpj, String razaoSocial,
                                  String emissao, String situacao,
                                  String moeda, List<BetaItemDTO> itens) {
        return new BetaPedidoDTO(numero, cnpj, razaoSocial, emissao, situacao, moeda, itens);
    }

    // =========================================================================
    // Pedido completo — payload oficial
    // =========================================================================

    @Test
    @DisplayName("Mapeamento do pedido 20260088412 do payload oficial")
    void toEntity_pedido412_mapeiaCorretamente() {
        BetaPedidoDTO dto = pedido(
                "20260088412",
                "12.345.678/0001-90",
                "Distribuidora Horizonte Ltda",
                "15/08/2026",
                "EM ABERTO",
                "BRL",
                List.of(itemPadrao())
        );

        Pedido pedido = betaIngestor.toEntity(dto);

        assertThat(pedido.getNumeroPedidoOrigem()).isEqualTo("20260088412");
        assertThat(pedido.getClienteOrigem()).isEqualTo("BETA");
        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.OPEN);
        assertThat(pedido.getMoeda()).isEqualTo("BRL");
        assertThat(pedido.getDataCriacao()).isEqualTo(BetaDateParser.parse("15/08/2026"));
        assertThat(pedido.getDataIngestao()).isNull(); // preenchido pelo PedidoService
    }

    @Test
    @DisplayName("clienteOrigem é sempre 'BETA'")
    void toEntity_qualquerDto_clienteOrigemEhBeta() {
        BetaPedidoDTO dto = pedido("X001", "12345678000190", "Empresa X",
                "01/01/2026", "EM ABERTO", "BRL", List.of(itemPadrao()));

        Pedido pedido = betaIngestor.toEntity(dto);

        assertThat(pedido.getClienteOrigem()).isEqualTo("BETA");
    }

    // =========================================================================
    // CNPJ — apenas dígitos
    // =========================================================================

    @Test
    @DisplayName("CNPJ com máscara é sanitizado para apenas dígitos")
    void toEntity_cnpjComMascara_somenteDigitos() {
        BetaPedidoDTO dto = pedido("X002", "12.345.678/0001-90", "Empresa",
                "01/01/2026", "EM ABERTO", "BRL", List.of(itemPadrao()));

        Pedido pedido = betaIngestor.toEntity(dto);

        assertThat(pedido.getFornecedor().getCnpj()).isEqualTo("12345678000190");
    }

    @Test
    @DisplayName("CNPJ já sem máscara permanece igual")
    void toEntity_cnpjSemMascara_permaneceIgual() {
        BetaPedidoDTO dto = pedido("X003", "98765432000155", "Empresa",
                "01/01/2026", "BLOQUEADO", "BRL", List.of(itemPadrao()));

        Pedido pedido = betaIngestor.toEntity(dto);

        assertThat(pedido.getFornecedor().getCnpj()).isEqualTo("98765432000155");
    }

    // =========================================================================
    // Status (vocabulário pt-BR)
    // =========================================================================

    @Test
    @DisplayName("'BLOQUEADO' mapeia para BLOCKED — pedido 20260088413")
    void toEntity_bloqueado_mapeiaParaBlocked() {
        BetaPedidoDTO dto = pedido("20260088413", "98.765.432/0001-55",
                "Frigorífico Boa Mesa S.A.", "01/08/2026",
                "BLOQUEADO", "BRL",
                List.of(item("1", "MAT-91", "Carne bovina", "KG",
                        "2.000,000", "0,000", "27,90")));

        Pedido pedido = betaIngestor.toEntity(dto);

        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.BLOCKED);
    }

    @Test
    @DisplayName("'ENCERRADO' mapeia para CLOSED")
    void toEntity_encerrado_mapeiaParaClosed() {
        BetaPedidoDTO dto = pedido("X004", "12345678000190", "Empresa",
                "01/01/2026", "ENCERRADO", "BRL", List.of(itemPadrao()));

        Pedido pedido = betaIngestor.toEntity(dto);

        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.CLOSED);
    }

    @Test
    @DisplayName("Status desconhecido lança IllegalArgumentException")
    void toEntity_statusDesconhecido_lancaExcecao() {
        BetaPedidoDTO dto = pedido("X005", "12345678000190", "Empresa",
                "01/01/2026", "PENDENTE", "BRL", List.of(itemPadrao()));

        assertThatThrownBy(() -> betaIngestor.toEntity(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PENDENTE");
    }

    // =========================================================================
    // Itens — conversão numérica BR e unidade de medida
    // =========================================================================

    @Test
    @DisplayName("Item com quantidades BR convertidas corretamente")
    void toEntity_itemComNumerosBr_converteCorretamente() {
        BetaItemDTO itemDto = item("1", "MAT-77", "Óleo de soja", "UN",
                "1.200,000", "400,000", "6,49");
        BetaPedidoDTO dto = pedido("X006", "12345678000190", "Empresa",
                "01/01/2026", "EM ABERTO", "BRL", List.of(itemDto));

        Pedido pedido = betaIngestor.toEntity(dto);
        Item item = pedido.getItens().getFirst();

        assertThat(item.getQuantidadePedida()).isEqualByComparingTo(new BigDecimal("1200.000"));
        assertThat(item.getQuantidadeRecebida()).isEqualByComparingTo(new BigDecimal("400.000"));
        assertThat(item.getPrecoUnitario()).isEqualByComparingTo(new BigDecimal("6.49"));
    }

    @Test
    @DisplayName("Item com quantidade recebida zero: 0,000 → BigDecimal 0")
    void toEntity_qtdRecebidaZero_retornaZero() {
        BetaItemDTO itemDto = item("1", "MAT-91", "Carne bovina", "KG",
                "2.000,000", "0,000", "27,90");
        BetaPedidoDTO dto = pedido("X007", "12345678000190", "Empresa",
                "01/01/2026", "EM ABERTO", "BRL", List.of(itemDto));

        Pedido pedido = betaIngestor.toEntity(dto);
        Item item = pedido.getItens().getFirst();

        assertThat(item.getQuantidadeRecebida()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Unidade de medida é persistida como vieram no CSV (UN, KG)")
    void toEntity_unidadeMedida_persistidaSemConversao() {
        BetaItemDTO itemUn = item("1", "MAT-1", "Produto", "UN", "1,000", "0,000", "1,00");
        BetaItemDTO itemKg = item("2", "MAT-2", "Produto", "KG", "1,000", "0,000", "1,00");
        BetaPedidoDTO dto = pedido("X008", "12345678000190", "Empresa",
                "01/01/2026", "EM ABERTO", "BRL", List.of(itemUn, itemKg));

        Pedido pedido = betaIngestor.toEntity(dto);

        assertThat(pedido.getItens().get(0).getUnidadeMedida()).isEqualTo("UN");
        assertThat(pedido.getItens().get(1).getUnidadeMedida()).isEqualTo("KG");
    }

    @Test
    @DisplayName("quantidade_pendente não é setada pelo ingestor (GENERATED ALWAYS no Postgres)")
    void toEntity_quantidadePendente_naoSetada() {
        BetaPedidoDTO dto = pedido("X009", "12345678000190", "Empresa",
                "01/01/2026", "EM ABERTO", "BRL", List.of(itemPadrao()));

        Pedido pedido = betaIngestor.toEntity(dto);

        assertThat(pedido.getItens()).allSatisfy(item ->
                assertThat(item.getQuantidadePendente()).isNull()
        );
    }

    // =========================================================================
    // Vínculo bidirecional Item → Pedido
    // =========================================================================

    @Test
    @DisplayName("Cada item referencia o pedido pai")
    void toEntity_itens_referenciaOPedidoPai() {
        BetaPedidoDTO dto = pedido("X010", "12345678000190", "Empresa",
                "01/01/2026", "EM ABERTO", "BRL",
                List.of(
                        item("1", "MAT-A", "Desc A", "UN", "10,000", "0,000", "1,00"),
                        item("2", "MAT-B", "Desc B", "KG", "5,000", "0,000", "2,00")
                ));

        Pedido pedido = betaIngestor.toEntity(dto);

        assertThat(pedido.getItens()).allSatisfy(i ->
                assertThat(i.getPedido()).isSameAs(pedido)
        );
    }

    @Test
    @DisplayName("Múltiplos itens mantêm a ordem original")
    void toEntity_variosItens_mantémOrdem() {
        BetaPedidoDTO dto = pedido("X011", "12345678000190", "Empresa",
                "01/01/2026", "EM ABERTO", "BRL",
                List.of(
                        item("1", "MAT-A", null, "UN", "1,000", "0,000", "1,00"),
                        item("2", "MAT-B", null, "UN", "2,000", "0,000", "2,00"),
                        item("3", "MAT-C", null, "UN", "3,000", "0,000", "3,00")
                ));

        Pedido pedido = betaIngestor.toEntity(dto);

        assertThat(pedido.getItens())
                .extracting(Item::getLinha)
                .containsExactly("1", "2", "3");
    }

    // =========================================================================
    // Fornecedor
    // =========================================================================

    @Test
    @DisplayName("Razão social com quebra de linha remontada é sanitizada (espaço)")
    void toEntity_razaoSocialComEspaco_sanitizada() {
        // Após remontagem o campo já vem como "Frigorífico Boa Mesa S.A." com espaço
        BetaPedidoDTO dto = pedido("X012", "12345678000190",
                "Frigorífico Boa Mesa S.A.",
                "01/01/2026", "EM ABERTO", "BRL", List.of(itemPadrao()));

        Pedido pedido = betaIngestor.toEntity(dto);

        assertThat(pedido.getFornecedor().getNome()).isEqualTo("Frigorífico Boa Mesa S.A.");
    }
}
