package com.v360.prosel.conectorpedidoscompra.service;

import com.v360.prosel.conectorpedidoscompra.dto.conferencia.ConferenciaResponseDTO;
import com.v360.prosel.conectorpedidoscompra.dto.conferencia.NotaFiscalDTO;
import com.v360.prosel.conectorpedidoscompra.dto.conferencia.NotaFiscalItemDTO;
import com.v360.prosel.conectorpedidoscompra.dto.conferencia.RelatorioConferenciasDTO;
import com.v360.prosel.conectorpedidoscompra.entity.Conferencia;
import com.v360.prosel.conectorpedidoscompra.entity.Fornecedor;
import com.v360.prosel.conectorpedidoscompra.entity.Item;
import com.v360.prosel.conectorpedidoscompra.entity.Pedido;
import com.v360.prosel.conectorpedidoscompra.enums.ResultadoConferencia;
import com.v360.prosel.conectorpedidoscompra.enums.StatusPedido;
import com.v360.prosel.conectorpedidoscompra.enums.TipoDivergencia;
import com.v360.prosel.conectorpedidoscompra.repository.ConferenciaRepository;
import com.v360.prosel.conectorpedidoscompra.repository.DivergenciaRepository;
import com.v360.prosel.conectorpedidoscompra.repository.PedidoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConferenciaServiceTest {

    private static final String CNPJ = "12345678000190";
    private static final String NUMERO = "PED-1";
    private static final String ORIGEM = "BETA";

    @Mock
    private PedidoRepository pedidoRepository;
    @Mock
    private ConferenciaRepository conferenciaRepository;
    @Mock
    private DivergenciaRepository divergenciaRepository;

    @InjectMocks
    private ConferenciaService service;

    @BeforeEach
    void persistirComId() {
        lenient().when(conferenciaRepository.save(any(Conferencia.class))).thenAnswer(invocation -> {
            Conferencia conferencia = invocation.getArgument(0);
            if (conferencia.getId() == null) {
                conferencia.setId(UUID.randomUUID());
            }
            return conferencia;
        });
    }

    @Test
    void pedidoNaoEncontrado_rejeitaEPersisteSemCruzarItens() {
        when(pedidoRepository.findByNumeroPedidoOrigemAndClienteOrigem(NUMERO, ORIGEM))
                .thenReturn(Optional.empty());

        ConferenciaResponseDTO resposta = service.conferir(nota(item("MAT-1", "1", "10.00")));

        assertThat(resposta.resultado()).isEqualTo(ResultadoConferencia.REJEITADA);
        assertThat(resposta.idPedido()).isNull();
        assertThat(resposta.divergencias()).extracting("tipo")
                .containsExactly(TipoDivergencia.PEDIDO_NAO_ENCONTRADO);

        ArgumentCaptor<Conferencia> captor = ArgumentCaptor.forClass(Conferencia.class);
        verify(conferenciaRepository).save(captor.capture());
        assertThat(captor.getValue().getPedido()).isNull();
        assertThat(captor.getValue().getFornecedorCnpj()).isEqualTo(CNPJ);
    }

    @Test
    void fornecedorDivergente() {
        Pedido pedido = pedidoAberto(itemPedido("MAT-1", "10", "0", "10", "10.00"));
        pedido.getFornecedor().setCnpj("00000000000000");
        when(pedidoRepository.findByNumeroPedidoOrigemAndClienteOrigem(NUMERO, ORIGEM))
                .thenReturn(Optional.of(pedido));

        ConferenciaResponseDTO resposta = service.conferir(nota(item("MAT-1", "1", "10.00")));

        assertThat(resposta.divergencias()).extracting("tipo")
                .contains(TipoDivergencia.FORNECEDOR_DIVERGENTE);
        assertThat(resposta.resultado()).isEqualTo(ResultadoConferencia.REJEITADA);
    }

    @Test
    void materialNaoEncontrado() {
        Pedido pedido = pedidoAberto(itemPedido("MAT-1", "10", "0", "10", "10.00"));
        when(pedidoRepository.findByNumeroPedidoOrigemAndClienteOrigem(NUMERO, ORIGEM))
                .thenReturn(Optional.of(pedido));

        ConferenciaResponseDTO resposta = service.conferir(nota(item("MAT-X", "1", "10.00")));

        assertThat(resposta.divergencias()).extracting("tipo")
                .containsExactly(TipoDivergencia.MATERIAL_NAO_ENCONTRADO);
    }

    @Test
    void quantidadeExcedePendente() {
        Pedido pedido = pedidoAberto(itemPedido("MAT-1", "10", "8", "2", "10.00"));
        when(pedidoRepository.findByNumeroPedidoOrigemAndClienteOrigem(NUMERO, ORIGEM))
                .thenReturn(Optional.of(pedido));

        ConferenciaResponseDTO resposta = service.conferir(nota(item("MAT-1", "3", "30.00")));

        assertThat(resposta.divergencias()).extracting("tipo")
                .containsExactly(TipoDivergencia.QUANTIDADE_EXCEDE_PENDENTE);
    }

    @Test
    void valorDivergenteAcimaDaTolerancia() {
        Pedido pedido = pedidoAberto(itemPedido("MAT-1", "10", "0", "10", "10.00"));
        when(pedidoRepository.findByNumeroPedidoOrigemAndClienteOrigem(NUMERO, ORIGEM))
                .thenReturn(Optional.of(pedido));

        ConferenciaResponseDTO resposta = service.conferir(nota(item("MAT-1", "1", "10.06")));

        assertThat(resposta.divergencias()).extracting("tipo")
                .containsExactly(TipoDivergencia.VALOR_DIVERGENTE);
    }

    @Test
    void valorNoLimiteDaTolerancia_naoGeraDivergencia() {
        Pedido pedido = pedidoAberto(itemPedido("MAT-1", "10", "0", "10", "10.00"));
        when(pedidoRepository.findByNumeroPedidoOrigemAndClienteOrigem(NUMERO, ORIGEM))
                .thenReturn(Optional.of(pedido));

        ConferenciaResponseDTO resposta = service.conferir(nota(item("MAT-1", "1", "10.05")));

        assertThat(resposta.resultado()).isEqualTo(ResultadoConferencia.APROVADA);
        assertThat(resposta.divergencias()).isEmpty();
    }

    @Test
    void pedidoBloqueado_registraEContinua() {
        Pedido pedido = pedidoAberto(itemPedido("MAT-1", "10", "0", "10", "10.00"));
        pedido.setStatus(StatusPedido.BLOCKED);
        when(pedidoRepository.findByNumeroPedidoOrigemAndClienteOrigem(NUMERO, ORIGEM))
                .thenReturn(Optional.of(pedido));

        ConferenciaResponseDTO resposta = service.conferir(nota(item("MAT-X", "1", "10.00")));

        assertThat(resposta.divergencias()).extracting("tipo")
                .containsExactly(
                        TipoDivergencia.PEDIDO_BLOQUEADO,
                        TipoDivergencia.MATERIAL_NAO_ENCONTRADO
                );
    }

    @Test
    void pedidoEncerrado_registraEContinua() {
        Pedido pedido = pedidoAberto(itemPedido("MAT-1", "10", "0", "10", "10.00"));
        pedido.setStatus(StatusPedido.CLOSED);
        when(pedidoRepository.findByNumeroPedidoOrigemAndClienteOrigem(NUMERO, ORIGEM))
                .thenReturn(Optional.of(pedido));

        ConferenciaResponseDTO resposta = service.conferir(nota(item("MAT-1", "20", "200.00")));

        assertThat(resposta.divergencias()).extracting("tipo")
                .contains(TipoDivergencia.PEDIDO_ENCERRADO, TipoDivergencia.QUANTIDADE_EXCEDE_PENDENTE);
    }

    @Test
    void aprovadaSemDivergencias() {
        Pedido pedido = pedidoAberto(itemPedido("MAT-1", "10", "0", "10", "10.00"));
        when(pedidoRepository.findByNumeroPedidoOrigemAndClienteOrigem(NUMERO, ORIGEM))
                .thenReturn(Optional.of(pedido));

        ConferenciaResponseDTO resposta = service.conferir(nota(item("MAT-1", "2", "20.00")));

        assertThat(resposta.resultado()).isEqualTo(ResultadoConferencia.APROVADA);
        assertThat(resposta.divergencias()).isEmpty();
        assertThat(resposta.idPedido()).isEqualTo(pedido.getId());
    }

    @Test
    void combinacaoNoMesmoPedido_acumulaTodasAsFalhas() {
        Pedido pedido = pedidoAberto(itemPedido("MAT-1", "10", "8", "2", "10.00"));
        pedido.setStatus(StatusPedido.BLOCKED);
        pedido.getFornecedor().setCnpj("99999999999999");
        when(pedidoRepository.findByNumeroPedidoOrigemAndClienteOrigem(NUMERO, ORIGEM))
                .thenReturn(Optional.of(pedido));

        ConferenciaResponseDTO resposta = service.conferir(nota(item("MAT-1", "3", "100.00")));

        assertThat(resposta.divergencias()).extracting("tipo")
                .contains(
                        TipoDivergencia.FORNECEDOR_DIVERGENTE,
                        TipoDivergencia.PEDIDO_BLOQUEADO,
                        TipoDivergencia.QUANTIDADE_EXCEDE_PENDENTE,
                        TipoDivergencia.VALOR_DIVERGENTE
                );
    }

    @Test
    void duasLinhasMesmoMaterial_agregaPendenteEPonderaPreco() {
        Item linha1 = itemPedido("MAT-1", "10", "0", "10", "10.00");
        linha1.setLinha("1");
        Item linha2 = itemPedido("MAT-1", "30", "0", "30", "20.00");
        linha2.setLinha("2");
        Pedido pedido = pedidoAberto(linha1, linha2);
        when(pedidoRepository.findByNumeroPedidoOrigemAndClienteOrigem(NUMERO, ORIGEM))
                .thenReturn(Optional.of(pedido));

        // preço ponderado = (10×10 + 30×20) / 40 = 17,50; 10 × 17,50 = 175
        ConferenciaResponseDTO ok = service.conferir(nota(
                item("MAT-1", "4", "70.00"),
                item("MAT-1", "6", "105.00")
        ));
        assertThat(ok.resultado()).isEqualTo(ResultadoConferencia.APROVADA);

        ConferenciaResponseDTO excede = service.conferir(nota(item("MAT-1", "41", "717.50")));
        assertThat(excede.divergencias()).extracting("tipo")
                .contains(TipoDivergencia.QUANTIDADE_EXCEDE_PENDENTE);
    }

    @Test
    void relatorio_agregaContagensPersistidas() {
        when(conferenciaRepository.countByResultado(ResultadoConferencia.APROVADA)).thenReturn(2L);
        when(conferenciaRepository.countByResultado(ResultadoConferencia.REJEITADA)).thenReturn(3L);
        when(divergenciaRepository.countByTipo(TipoDivergencia.PEDIDO_NAO_ENCONTRADO)).thenReturn(1L);
        when(divergenciaRepository.countByTipo(TipoDivergencia.FORNECEDOR_DIVERGENTE)).thenReturn(0L);
        when(divergenciaRepository.countByTipo(TipoDivergencia.MATERIAL_NAO_ENCONTRADO)).thenReturn(1L);
        when(divergenciaRepository.countByTipo(TipoDivergencia.QUANTIDADE_EXCEDE_PENDENTE)).thenReturn(2L);
        when(divergenciaRepository.countByTipo(TipoDivergencia.VALOR_DIVERGENTE)).thenReturn(1L);
        when(divergenciaRepository.countByTipo(TipoDivergencia.PEDIDO_BLOQUEADO)).thenReturn(0L);
        when(divergenciaRepository.countByTipo(TipoDivergencia.PEDIDO_ENCERRADO)).thenReturn(1L);

        RelatorioConferenciasDTO relatorio = service.relatorio();

        assertThat(relatorio.total()).isEqualTo(5L);
        assertThat(relatorio.porResultado()).containsEntry("APROVADA", 2L).containsEntry("REJEITADA", 3L);
        assertThat(relatorio.porTipo()).containsEntry("QUANTIDADE_EXCEDE_PENDENTE", 2L);
    }

    private NotaFiscalDTO nota(NotaFiscalItemDTO... itens) {
        return new NotaFiscalDTO(NUMERO, ORIGEM, "12.345.678/0001-90", List.of(itens));
    }

    private NotaFiscalItemDTO item(String material, String quantidade, String valor) {
        return new NotaFiscalItemDTO(material, new BigDecimal(quantidade), new BigDecimal(valor));
    }

    private Pedido pedidoAberto(Item... itens) {
        Fornecedor fornecedor = new Fornecedor();
        fornecedor.setId(UUID.randomUUID());
        fornecedor.setCnpj(CNPJ);
        fornecedor.setNome("Fornecedor");

        Pedido pedido = new Pedido();
        pedido.setId(UUID.randomUUID());
        pedido.setNumeroPedidoOrigem(NUMERO);
        pedido.setClienteOrigem(ORIGEM);
        pedido.setStatus(StatusPedido.OPEN);
        pedido.setFornecedor(fornecedor);
        pedido.setItens(new ArrayList<>(List.of(itens)));
        for (Item item : itens) {
            item.setPedido(pedido);
        }
        return pedido;
    }

    private Item itemPedido(String material, String pedida, String recebida, String pendente, String preco) {
        Item item = new Item();
        item.setId(UUID.randomUUID());
        item.setLinha("1");
        item.setCodigoMaterial(material);
        item.setQuantidadePedida(new BigDecimal(pedida));
        item.setQuantidadeRecebida(new BigDecimal(recebida));
        item.setQuantidadePendente(new BigDecimal(pendente));
        item.setPrecoUnitario(new BigDecimal(preco));
        return item;
    }
}
