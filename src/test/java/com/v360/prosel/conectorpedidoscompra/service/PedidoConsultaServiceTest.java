package com.v360.prosel.conectorpedidoscompra.service;

import com.v360.prosel.conectorpedidoscompra.dto.pedido.PedidoResumoResponseDTO;
import com.v360.prosel.conectorpedidoscompra.dto.pedido.PedidoResponseDTO;
import com.v360.prosel.conectorpedidoscompra.entity.Fornecedor;
import com.v360.prosel.conectorpedidoscompra.entity.Item;
import com.v360.prosel.conectorpedidoscompra.entity.Pedido;
import com.v360.prosel.conectorpedidoscompra.enums.StatusPedido;
import com.v360.prosel.conectorpedidoscompra.repository.PedidoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PedidoConsultaServiceTest {

    @Mock
    private PedidoRepository pedidoRepository;

    @InjectMocks
    private PedidoConsultaService service;

    @Test
    void consultar_filtroClienteOrigemIsolado_delegaQuerySemPendencia() {
        Pedido pedido = pedidoComItens();
        when(pedidoRepository.findResumoByFiltros("BETA", null, null)).thenReturn(List.of(pedido));

        List<PedidoResumoResponseDTO> resposta = service.consultar("BETA", null, null, null);

        verify(pedidoRepository).findResumoByFiltros("BETA", null, null);
        assertThat(resposta).hasSize(1);
        assertThat(resposta.getFirst().clienteOrigem()).isEqualTo("BETA");
        assertThat(resposta.getFirst().numeroPedido()).isEqualTo("PED-1");
    }

    @Test
    void consultar_filtroFornecedorIsolado_sanitizaCnpj() {
        when(pedidoRepository.findResumoByFiltros(null, "12345678000190", null)).thenReturn(List.of());

        service.consultar(null, "12.345.678/0001-90", null, null);

        verify(pedidoRepository).findResumoByFiltros(null, "12345678000190", null);
    }

    @Test
    void consultar_filtroStatusIsolado() {
        when(pedidoRepository.findResumoByFiltros(null, null, StatusPedido.OPEN)).thenReturn(List.of());

        service.consultar(null, null, StatusPedido.OPEN, null);

        verify(pedidoRepository).findResumoByFiltros(null, null, StatusPedido.OPEN);
    }

    @Test
    void consultar_combinacaoClienteEStatus() {
        when(pedidoRepository.findResumoByFiltros("ALFA", null, StatusPedido.BLOCKED)).thenReturn(List.of());

        service.consultar("ALFA", null, StatusPedido.BLOCKED, null);

        verify(pedidoRepository).findResumoByFiltros("ALFA", null, StatusPedido.BLOCKED);
        verifyNoMoreInteractions(pedidoRepository);
    }

    @Test
    void consultar_comPendenciaTrue_usaExists() {
        when(pedidoRepository.findResumoByFiltrosComPendencia(null, null, null)).thenReturn(List.of());

        service.consultar(null, null, null, true);

        verify(pedidoRepository).findResumoByFiltrosComPendencia(null, null, null);
    }

    @Test
    void consultar_comPendenciaFalse_usaNotExists() {
        when(pedidoRepository.findResumoByFiltrosSemPendencia(null, null, null)).thenReturn(List.of());

        service.consultar(null, null, null, false);

        verify(pedidoRepository).findResumoByFiltrosSemPendencia(null, null, null);
    }

    @Test
    void consultar_listaNaoIncluiItens() {
        Pedido pedido = pedidoComItens();
        when(pedidoRepository.findResumoByFiltros(null, null, null)).thenReturn(List.of(pedido));

        PedidoResumoResponseDTO resumo = service.consultar(null, null, null, null).getFirst();

        assertThat(resumo.fornecedor().cnpj()).isEqualTo("12345678000190");
        assertThat(resumo.fornecedor().idFornecedor()).isEqualTo(pedido.getFornecedor().getId());
    }

    @Test
    void consultarPorId_detalheComVariosItens_usaPendenteDoBanco() {
        Pedido pedido = pedidoComItens();
        when(pedidoRepository.findDetalheById(pedido.getId())).thenReturn(Optional.of(pedido));

        PedidoResponseDTO detalhe = service.consultarPorId(pedido.getId());

        assertThat(detalhe.itens()).hasSize(2);
        assertThat(detalhe.itens().getFirst().quantidadePendente()).isEqualByComparingTo("5");
        assertThat(detalhe.itens().get(1).quantidadePendente()).isEqualByComparingTo("0");
        assertThat(detalhe.itens().getFirst().quantidadePendente())
                .isEqualByComparingTo(detalhe.itens().getFirst().quantidadePedida()
                        .subtract(detalhe.itens().getFirst().quantidadeRecebida()));
    }

    @Test
    void consultarPorId_inexistente_lanca404() {
        UUID id = UUID.randomUUID();
        when(pedidoRepository.findDetalheById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.consultarPorId(id))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    private Pedido pedidoComItens() {
        Fornecedor fornecedor = new Fornecedor();
        fornecedor.setId(UUID.randomUUID());
        fornecedor.setCnpj("12345678000190");
        fornecedor.setNome("Fornecedor");

        Item parcial = item("1", "MAT-1", new BigDecimal("7"), new BigDecimal("2"), new BigDecimal("5"));
        Item recebido = item("2", "MAT-2", new BigDecimal("3"), new BigDecimal("3"), new BigDecimal("0"));

        Pedido pedido = new Pedido();
        pedido.setId(UUID.randomUUID());
        pedido.setClienteOrigem("BETA");
        pedido.setNumeroPedidoOrigem("PED-1");
        pedido.setStatus(StatusPedido.OPEN);
        pedido.setDataCriacao(Instant.parse("2026-08-05T03:00:00Z"));
        pedido.setMoeda("BRL");
        pedido.setFornecedor(fornecedor);
        pedido.setItens(List.of(parcial, recebido));
        parcial.setPedido(pedido);
        recebido.setPedido(pedido);
        return pedido;
    }

    private Item item(String linha, String material, BigDecimal pedida, BigDecimal recebida, BigDecimal pendente) {
        Item item = new Item();
        item.setId(UUID.randomUUID());
        item.setLinha(linha);
        item.setCodigoMaterial(material);
        item.setQuantidadePedida(pedida);
        item.setQuantidadeRecebida(recebida);
        item.setQuantidadePendente(pendente);
        item.setPrecoUnitario(new BigDecimal("10.00"));
        return item;
    }
}
