package com.v360.prosel.conectorpedidoscompra.ingestor;

import com.v360.prosel.conectorpedidoscompra.dto.alfa.AlfaFornecedorDTO;
import com.v360.prosel.conectorpedidoscompra.dto.alfa.AlfaItemDTO;
import com.v360.prosel.conectorpedidoscompra.dto.alfa.AlfaPedidoDTO;
import com.v360.prosel.conectorpedidoscompra.entity.Item;
import com.v360.prosel.conectorpedidoscompra.entity.Pedido;
import com.v360.prosel.conectorpedidoscompra.enums.StatusPedido;
import com.v360.prosel.conectorpedidoscompra.normalizer.AlfaDateParser;
import com.v360.prosel.conectorpedidoscompra.normalizer.AlfaStatusMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Teste unitário puro do AlfaIngestor.
 *
 * Sem @SpringBootTest, sem H2, sem banco, sem Mockito.
 * O AlfaIngestor é pura lógica de transformação DTO → entidade;
 * sua única dependência (AlfaStatusMapper) não tem dependências externas,
 * então é instanciada diretamente com new. (DESING.md seção 4.3)
 */
class AlfaIngestorTest {

    private AlfaIngestor alfaIngestor;

    // Dado fixo para reutilizar nos testes que não variam data/moeda
    private static final LocalDate DATA_CRIACAO = LocalDate.of(2026, 8, 15);

    @BeforeEach
    void setUp() {
        // AlfaStatusMapper não tem dependências externas — instanciado diretamente.
        alfaIngestor = new AlfaIngestor(new AlfaStatusMapper());
    }

    // =========================================================================
    // Helpers — montagem de DTOs de forma concisa
    // =========================================================================

    private AlfaFornecedorDTO fornecedor(String cnpj, String nome) {
        return new AlfaFornecedorDTO(cnpj, nome);
    }

    private AlfaItemDTO item(String linha, String codigoMaterial, String descricao,
                              String unidadeMedida, BigDecimal pedida,
                              BigDecimal recebida, BigDecimal preco) {
        return new AlfaItemDTO(linha, codigoMaterial, descricao, unidadeMedida, pedida, recebida, preco);
    }

    /** Item com valores padrão para testes que não focam nos detalhes do item. */
    private AlfaItemDTO itemPadrao() {
        return item("1", "MAT-001", "Produto de teste", "UN",
                new BigDecimal("10.0000"), new BigDecimal("3.0000"), new BigDecimal("99.9900"));
    }

    private AlfaPedidoDTO pedido(String numero, String status,
                                  AlfaFornecedorDTO forn, List<AlfaItemDTO> itens) {
        return new AlfaPedidoDTO(numero, DATA_CRIACAO, status, "BRL", forn, itens);
    }

    // =========================================================================
    // Testes formatados no padrão metodo_cenario_resultado
    // =========================================================================

    @Test
    @DisplayName("Mapeamento completo do cabeçalho de um pedido OPEN")
    void toEntity_pedidoValido_mapeiaCabecalhoComSucesso() {
        AlfaPedidoDTO dto = pedido("AL-001", "OPEN",
                fornecedor("12345678000190", "Fornecedor Teste"),
                List.of(itemPadrao()));

        Pedido resultado = alfaIngestor.toEntity(dto);

        assertThat(resultado.getNumeroPedidoOrigem()).isEqualTo("AL-001");
        assertThat(resultado.getClienteOrigem()).isEqualTo("ALFA");
        assertThat(resultado.getDataCriacao()).isEqualTo(AlfaDateParser.parse(DATA_CRIACAO));
        assertThat(resultado.getStatus()).isEqualTo(StatusPedido.OPEN);
        assertThat(resultado.getMoeda()).isEqualTo("BRL");
        assertThat(resultado.getFornecedor().getCnpj()).isEqualTo("12345678000190");
        assertThat(resultado.getFornecedor().getNome()).isEqualTo("Fornecedor Teste");
        assertThat(resultado.getItens()).hasSize(1);
        assertThat(resultado.getDataIngestao()).isNull();
    }

    @Test
    @DisplayName("Sanitização de CNPJ com máscara")
    void toEntity_cnpjComMascara_removeMascara() {
        AlfaPedidoDTO dto = pedido("AL-002", "OPEN",
                fornecedor("12.345.678/0001-90", "Fornecedor com Máscara"),
                List.of(itemPadrao()));

        Pedido resultado = alfaIngestor.toEntity(dto);

        assertThat(resultado.getFornecedor().getCnpj()).isEqualTo("12345678000190");
    }

    @Test
    @DisplayName("Tradução do status CLOSED")
    void toEntity_statusClosed_mapeiaParaEnumClosed() {
        AlfaPedidoDTO dto = pedido("AL-003", "CLOSED",
                fornecedor("12345678000190", "Fornecedor Teste"),
                List.of(itemPadrao()));

        Pedido resultado = alfaIngestor.toEntity(dto);

        assertThat(resultado.getStatus()).isEqualTo(StatusPedido.CLOSED);
    }

    @Test
    @DisplayName("Tradução do status BLOCKED")
    void toEntity_statusBlocked_mapeiaParaEnumBlocked() {
        AlfaPedidoDTO dto = pedido("AL-004", "BLOCKED",
                fornecedor("12345678000190", "Fornecedor Teste"),
                List.of(itemPadrao()));

        Pedido resultado = alfaIngestor.toEntity(dto);

        assertThat(resultado.getStatus()).isEqualTo(StatusPedido.BLOCKED);
    }

    @Test
    @DisplayName("Tradução de status enviado em minúsculo")
    void toEntity_statusLowercase_mapeiaParaEnumOpen() {
        AlfaPedidoDTO dto = pedido("AL-005", "open",
                fornecedor("12345678000190", "Fornecedor Teste"),
                List.of(itemPadrao()));

        Pedido resultado = alfaIngestor.toEntity(dto);

        assertThat(resultado.getStatus()).isEqualTo(StatusPedido.OPEN);
    }

    @Test
    @DisplayName("Tratamento de status desconhecido/inválido")
    void toEntity_statusInvalido_lancaExcecao() {
        AlfaPedidoDTO dto = pedido("AL-006", "PENDENTE",
                fornecedor("12345678000190", "Fornecedor Teste"),
                List.of(itemPadrao()));

        assertThatThrownBy(() -> alfaIngestor.toEntity(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PENDENTE");
    }

    @Test
    @DisplayName("Mapeamento completo de todos os atributos de um item")
    void toEntity_itemCompleto_mapeiaTodosOsCampos() {
        AlfaItemDTO itemDto = item(
                "1", "MAT-001", "Produto de teste", "UN",
                new BigDecimal("10.0000"), new BigDecimal("3.0000"), new BigDecimal("99.9900")
        );
        AlfaPedidoDTO dto = pedido("AL-007", "OPEN",
                fornecedor("12345678000190", "Fornecedor Teste"),
                List.of(itemDto));

        Pedido resultado = alfaIngestor.toEntity(dto);
        Item item = resultado.getItens().getFirst();

        assertThat(item.getLinha()).isEqualTo("1");
        assertThat(item.getCodigoMaterial()).isEqualTo("MAT-001");
        assertThat(item.getDescricao()).isEqualTo("Produto de teste");
        assertThat(item.getUnidadeMedida()).isEqualTo("UN");
        assertThat(item.getQuantidadePedida()).isEqualByComparingTo(new BigDecimal("10.0000"));
        assertThat(item.getQuantidadeRecebida()).isEqualByComparingTo(new BigDecimal("3.0000"));
        assertThat(item.getPrecoUnitario()).isEqualByComparingTo(new BigDecimal("99.9900"));
        // quantidade_pendente é GENERATED ALWAYS no Postgres — não preenchida pelo backend
        assertThat(item.getQuantidadePendente()).isNull();
    }

    @Test
    @DisplayName("Mapeamento de lista com múltiplos itens mantendo a ordem")
    void toEntity_variosItens_mapeiaTodosNaOrdem() {
        List<AlfaItemDTO> itens = List.of(
                item("1", "MAT-A", null, "UN", BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.TEN),
                item("2", "MAT-B", null, "KG", BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.TEN),
                item("3", "MAT-C", null, "CX", BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.TEN)
        );
        AlfaPedidoDTO dto = pedido("AL-008", "OPEN",
                fornecedor("12345678000190", "Fornecedor Teste"), itens);

        Pedido resultado = alfaIngestor.toEntity(dto);

        assertThat(resultado.getItens()).hasSize(3);
        assertThat(resultado.getItens())
                .extracting(Item::getLinha)
                .containsExactly("1", "2", "3");
    }

    @Test
    @DisplayName("Referência bidirecional do Item para o Pedido pai")
    void toEntity_comItens_associaItemAoPedido() {
        List<AlfaItemDTO> itens = List.of(
                item("1", "MAT-X", null, "UN", BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.TEN),
                item("2", "MAT-Y", null, "UN", BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.TEN)
        );
        AlfaPedidoDTO dto = pedido("AL-009", "OPEN",
                fornecedor("12345678000190", "Fornecedor Teste"), itens);

        Pedido resultado = alfaIngestor.toEntity(dto);

        assertThat(resultado.getItens()).allSatisfy(item ->
                assertThat(item.getPedido()).isSameAs(resultado)
        );
    }

    @Test
    @DisplayName("Tratamento de lista de itens vazia — contrato interno do ingestor")
    void toEntity_listaItensVazia_retornaPedidoSemItens() {
        AlfaPedidoDTO dto = pedido("AL-010", "OPEN",
                fornecedor("12345678000190", "Fornecedor Teste"),
                Collections.emptyList());

        Pedido resultado = alfaIngestor.toEntity(dto);

        assertThat(resultado.getItens()).isEmpty();
    }

    @Test
    @DisplayName("Sanitização de quebra de linha no nome do fornecedor")
    void toEntity_nomeComQuebraLinha_removeQuebraLinha() {
        AlfaPedidoDTO dto = pedido("AL-011", "OPEN",
                fornecedor("12345678000190", "Metalúrgica São Jorge\nS.A."),
                List.of(itemPadrao()));

        Pedido resultado = alfaIngestor.toEntity(dto);

        assertThat(resultado.getFornecedor().getNome())
                .isEqualTo("Metalúrgica São Jorge S.A.");
    }

    @Test
    @DisplayName("Sanitização de quebra de linha na descrição do item")
    void toEntity_descricaoComQuebraLinha_removeQuebraLinha() {
        AlfaItemDTO itemComQuebraLinha = item(
                "1", "MAT-001", "Produto\ncom quebra\nde linha", "UN",
                new BigDecimal("10.0000"), new BigDecimal("3.0000"), new BigDecimal("99.9900")
        );
        AlfaPedidoDTO dto = pedido("AL-012", "OPEN",
                fornecedor("12345678000190", "Fornecedor Teste"),
                List.of(itemComQuebraLinha));

        Pedido resultado = alfaIngestor.toEntity(dto);

        assertThat(resultado.getItens().getFirst().getDescricao())
                .isEqualTo("Produto com quebra de linha");
    }
}
