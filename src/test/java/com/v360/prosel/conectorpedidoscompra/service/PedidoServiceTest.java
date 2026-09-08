package com.v360.prosel.conectorpedidoscompra.service;

import com.v360.prosel.conectorpedidoscompra.entity.Fornecedor;
import com.v360.prosel.conectorpedidoscompra.entity.Item;
import com.v360.prosel.conectorpedidoscompra.entity.Pedido;
import com.v360.prosel.conectorpedidoscompra.enums.StatusPedido;
import com.v360.prosel.conectorpedidoscompra.repository.DivergenciaRepository;
import com.v360.prosel.conectorpedidoscompra.repository.FornecedorRepository;
import com.v360.prosel.conectorpedidoscompra.repository.ItemRepository;
import com.v360.prosel.conectorpedidoscompra.repository.PedidoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Teste unitário do PedidoService com Mockito.
 *
 * Sem @SpringBootTest, sem banco. Os três repositórios são mockados para
 * isolar e testar exclusivamente a lógica de orquestração do upsert
 * (DESING.md seção 4.3).
 */
@ExtendWith(MockitoExtension.class)
class PedidoServiceTest {

    @Mock
    private PedidoRepository pedidoRepository;

    @Mock
    private FornecedorRepository fornecedorRepository;

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private DivergenciaRepository divergenciaRepository;

    @InjectMocks
    private PedidoService pedidoService;

    // =========================================================================
    // Helpers — montagem de entidades transientes de forma concisa
    // =========================================================================

    private Fornecedor fornecedorTransiente(String cnpj, String nome) {
        Fornecedor f = new Fornecedor();
        f.setCnpj(cnpj);
        f.setNome(nome);
        return f;
    }

    private Fornecedor fornecedorPersistido(String cnpj, String nome) {
        Fornecedor f = fornecedorTransiente(cnpj, nome);
        f.setId(UUID.randomUUID());
        return f;
    }

    private Item itemTransiente(String linha, String codigoMaterial) {
        Item item = new Item();
        item.setLinha(linha);
        item.setCodigoMaterial(codigoMaterial);
        item.setDescricao("Descrição " + codigoMaterial);
        item.setUnidadeMedida("UN");
        item.setQuantidadePedida(new BigDecimal("10.0000"));
        item.setQuantidadeRecebida(new BigDecimal("3.0000"));
        item.setPrecoUnitario(new BigDecimal("99.9900"));
        return item;
    }

    private Pedido pedidoTransiente(String numero, Fornecedor fornecedor, List<Item> itens) {
        Pedido p = new Pedido();
        p.setNumeroPedidoOrigem(numero);
        p.setClienteOrigem("ALFA");
        p.setStatus(StatusPedido.OPEN);
        p.setDataCriacao(Instant.parse("2026-08-05T03:00:00Z"));
        p.setMoeda("BRL");
        p.setFornecedor(fornecedor);
        p.setItens(new ArrayList<>(itens));
        return p;
    }

    private Pedido pedidoPersistido(String numero, Fornecedor fornecedor) {
        Pedido p = pedidoTransiente(numero, fornecedor, List.of());
        p.setId(UUID.randomUUID());
        return p;
    }

    // =========================================================================
    // Testes — Fornecedor
    // =========================================================================

    @Test
    @DisplayName("Fornecedor novo — persiste chamando save no repositório")
    void upsert_fornecedorNovo_salvaNoBanco() {
        Fornecedor forn = fornecedorTransiente("12345678000190", "Fornecedor Novo");
        Fornecedor fornSalvo = fornecedorPersistido("12345678000190", "Fornecedor Novo");
        Pedido incoming = pedidoTransiente("AL-001", forn, List.of(itemTransiente("1", "MAT-001")));
        Pedido pedidoSalvo = pedidoPersistido("AL-001", fornSalvo);

        when(fornecedorRepository.findByCnpj("12345678000190")).thenReturn(Optional.empty());
        when(fornecedorRepository.save(forn)).thenReturn(fornSalvo);
        when(pedidoRepository.findByNumeroPedidoOrigemAndClienteOrigem("AL-001", "ALFA"))
                .thenReturn(Optional.empty());
        when(pedidoRepository.save(any())).thenReturn(pedidoSalvo);
        when(pedidoRepository.findById(pedidoSalvo.getId())).thenReturn(Optional.of(pedidoSalvo));
        when(itemRepository.findByPedidoAndLinha(any(), any())).thenReturn(Optional.empty());
        when(itemRepository.save(any())).thenReturn(new Item());

        pedidoService.upsert(incoming);

        verify(fornecedorRepository, times(1)).save(forn);
    }

    @Test
    @DisplayName("Fornecedor existente — atualiza o nome e chama save no existente")
    void upsert_fornecedorExistente_atualizaNome() {
        Fornecedor fornExistente = fornecedorPersistido("12345678000190", "Nome Antigo");
        Fornecedor forn = fornecedorTransiente("12345678000190", "Nome Novo");
        Pedido incoming = pedidoTransiente("AL-002", forn, List.of());
        Pedido pedidoSalvo = pedidoPersistido("AL-002", fornExistente);

        when(fornecedorRepository.findByCnpj("12345678000190")).thenReturn(Optional.of(fornExistente));
        when(fornecedorRepository.save(fornExistente)).thenReturn(fornExistente);
        when(pedidoRepository.findByNumeroPedidoOrigemAndClienteOrigem("AL-002", "ALFA"))
                .thenReturn(Optional.empty());
        when(pedidoRepository.save(any())).thenReturn(pedidoSalvo);
        when(pedidoRepository.findById(pedidoSalvo.getId())).thenReturn(Optional.of(pedidoSalvo));

        pedidoService.upsert(incoming);

        assertThat(fornExistente.getNome()).isEqualTo("Nome Novo");
        verify(fornecedorRepository, times(1)).save(fornExistente);
    }

    // =========================================================================
    // Testes — Pedido novo (caminho CREATE)
    // =========================================================================

    @Test
    @DisplayName("Pedido novo — dataIngestao é preenchida automaticamente")
    void upsert_pedidoNovo_criaComDataIngestao() {
        Fornecedor forn = fornecedorTransiente("12345678000190", "Fornecedor");
        Fornecedor fornSalvo = fornecedorPersistido("12345678000190", "Fornecedor");
        Pedido incoming = pedidoTransiente("AL-003", forn, List.of());
        ArgumentCaptor<Pedido> pedidoCaptor = ArgumentCaptor.forClass(Pedido.class);
        Pedido pedidoSalvo = pedidoPersistido("AL-003", fornSalvo);

        when(fornecedorRepository.findByCnpj(any())).thenReturn(Optional.empty());
        when(fornecedorRepository.save(any())).thenReturn(fornSalvo);
        when(pedidoRepository.findByNumeroPedidoOrigemAndClienteOrigem(any(), any()))
                .thenReturn(Optional.empty());
        when(pedidoRepository.save(pedidoCaptor.capture())).thenReturn(pedidoSalvo);
        when(pedidoRepository.findById(pedidoSalvo.getId())).thenReturn(Optional.of(pedidoSalvo));

        pedidoService.upsert(incoming);

        assertThat(pedidoCaptor.getValue().getDataIngestao()).isNotNull();
    }

    @Test
    @DisplayName("Pedido novo com 2 itens — itemRepository.save() é chamado 2 vezes (teste de regressão do bug do .clear())")
    void upsert_pedidoNovo_itensDevemSerSalvos() {
        Fornecedor forn = fornecedorTransiente("12345678000190", "Fornecedor");
        Fornecedor fornSalvo = fornecedorPersistido("12345678000190", "Fornecedor");
        List<Item> itens = List.of(itemTransiente("1", "MAT-001"), itemTransiente("2", "MAT-002"));
        Pedido incoming = pedidoTransiente("AL-004", forn, itens);
        Pedido pedidoSalvo = pedidoPersistido("AL-004", fornSalvo);

        when(fornecedorRepository.findByCnpj(any())).thenReturn(Optional.empty());
        when(fornecedorRepository.save(any())).thenReturn(fornSalvo);
        when(pedidoRepository.findByNumeroPedidoOrigemAndClienteOrigem(any(), any()))
                .thenReturn(Optional.empty());
        when(pedidoRepository.save(any())).thenReturn(pedidoSalvo);
        when(pedidoRepository.findById(pedidoSalvo.getId())).thenReturn(Optional.of(pedidoSalvo));
        when(itemRepository.findByPedidoAndLinha(any(), any())).thenReturn(Optional.empty());
        when(itemRepository.save(any())).thenReturn(new Item());

        pedidoService.upsert(incoming);

        // Verifica que ambos os itens foram processados — regressão do bug do .clear()
        verify(itemRepository, times(2)).save(any(Item.class));
    }

    // =========================================================================
    // Testes — Pedido existente (caminho UPDATE)
    // =========================================================================

    @Test
    @DisplayName("Pedido existente — status, dataCriacao e moeda do incoming são aplicados no existente")
    void upsert_pedidoExistente_atualizaCamposCorretamente() {
        Fornecedor fornSalvo = fornecedorPersistido("12345678000190", "Fornecedor");
        Pedido existente = pedidoPersistido("AL-005", fornSalvo);
        existente.setStatus(StatusPedido.OPEN);

        Fornecedor forn = fornecedorTransiente("12345678000190", "Fornecedor");
        Pedido incoming = pedidoTransiente("AL-005", forn, List.of());
        incoming.setStatus(StatusPedido.CLOSED);
        incoming.setMoeda("USD");
        Instant novaData = Instant.parse("2026-09-01T03:00:00Z");
        incoming.setDataCriacao(novaData);

        when(fornecedorRepository.findByCnpj(any())).thenReturn(Optional.of(fornSalvo));
        when(fornecedorRepository.save(any())).thenReturn(fornSalvo);
        when(pedidoRepository.findByNumeroPedidoOrigemAndClienteOrigem("AL-005", "ALFA"))
                .thenReturn(Optional.of(existente));
        when(pedidoRepository.save(existente)).thenReturn(existente);
        when(pedidoRepository.findById(existente.getId())).thenReturn(Optional.of(existente));

        pedidoService.upsert(incoming);

        assertThat(existente.getStatus()).isEqualTo(StatusPedido.CLOSED);
        assertThat(existente.getMoeda()).isEqualTo("USD");
        assertThat(existente.getDataCriacao()).isEqualTo(novaData);
        assertThat(existente.getDataIngestao()).isNotNull();
    }

    @Test
    @DisplayName("Pedido existente com item novo — criarItem é invocado (save no item)")
    void upsert_pedidoExistente_itemNovo_criaItem() {
        Fornecedor fornSalvo = fornecedorPersistido("12345678000190", "Fornecedor");
        Pedido existente = pedidoPersistido("AL-006", fornSalvo);
        Fornecedor forn = fornecedorTransiente("12345678000190", "Fornecedor");
        Pedido incoming = pedidoTransiente("AL-006", forn, List.of(itemTransiente("1", "MAT-001")));

        when(fornecedorRepository.findByCnpj(any())).thenReturn(Optional.of(fornSalvo));
        when(fornecedorRepository.save(any())).thenReturn(fornSalvo);
        when(pedidoRepository.findByNumeroPedidoOrigemAndClienteOrigem("AL-006", "ALFA"))
                .thenReturn(Optional.of(existente));
        when(pedidoRepository.save(any())).thenReturn(existente);
        when(pedidoRepository.findById(existente.getId())).thenReturn(Optional.of(existente));
        // Item não existe ainda para este pedido
        when(itemRepository.findByPedidoAndLinha(any(), eq("1"))).thenReturn(Optional.empty());
        when(itemRepository.save(any())).thenReturn(new Item());

        pedidoService.upsert(incoming);

        verify(itemRepository, times(1)).save(any(Item.class));
    }

    @Test
    @DisplayName("Pedido existente com item já existente — atualizarItem é invocado (save no existente)")
    void upsert_pedidoExistente_itemExistente_atualizaItem() {
        Fornecedor fornSalvo = fornecedorPersistido("12345678000190", "Fornecedor");
        Pedido existente = pedidoPersistido("AL-007", fornSalvo);
        Fornecedor forn = fornecedorTransiente("12345678000190", "Fornecedor");
        Pedido incoming = pedidoTransiente("AL-007", forn, List.of(itemTransiente("1", "MAT-001")));

        Item itemExistente = itemTransiente("1", "MAT-001");
        itemExistente.setPedido(existente);

        when(fornecedorRepository.findByCnpj(any())).thenReturn(Optional.of(fornSalvo));
        when(fornecedorRepository.save(any())).thenReturn(fornSalvo);
        when(pedidoRepository.findByNumeroPedidoOrigemAndClienteOrigem("AL-007", "ALFA"))
                .thenReturn(Optional.of(existente));
        when(pedidoRepository.save(any())).thenReturn(existente);
        when(pedidoRepository.findById(existente.getId())).thenReturn(Optional.of(existente));
        // Item já existe para este pedido
        when(itemRepository.findByPedidoAndLinha(any(), eq("1"))).thenReturn(Optional.of(itemExistente));
        when(itemRepository.save(itemExistente)).thenReturn(itemExistente);

        pedidoService.upsert(incoming);

        // save deve ser chamado no item existente (atualização), não em um item novo
        verify(itemRepository, times(1)).save(itemExistente);
    }

    @Test
    @DisplayName("Linha órfã sem recebimento e histórico é removida")
    void upsert_linhaOrfaSemHistorico_remove() {
        Fornecedor fornecedor = fornecedorPersistido("12345678000190", "Fornecedor");
        Pedido existente = pedidoPersistido("AL-008", fornecedor);
        Item orfao = itemTransiente("1", "MAT-ORFAO");
        orfao.setPedido(existente);
        orfao.setQuantidadeRecebida(BigDecimal.ZERO);

        Pedido incoming = pedidoTransiente("AL-008", fornecedorTransiente(fornecedor.getCnpj(), fornecedor.getNome()),
                List.of());
        when(fornecedorRepository.findByCnpj(any())).thenReturn(Optional.of(fornecedor));
        when(fornecedorRepository.save(any())).thenReturn(fornecedor);
        when(pedidoRepository.findByNumeroPedidoOrigemAndClienteOrigem("AL-008", "ALFA"))
                .thenReturn(Optional.of(existente));
        when(pedidoRepository.save(existente)).thenReturn(existente);
        when(pedidoRepository.findById(existente.getId())).thenReturn(Optional.of(existente));
        when(itemRepository.findByPedido(existente)).thenReturn(List.of(orfao));
        when(divergenciaRepository.existsByConferencia_Pedido_IdAndCodigoMaterial(
                existente.getId(), "MAT-ORFAO")).thenReturn(false);

        pedidoService.upsert(incoming);

        verify(itemRepository).delete(orfao);
    }

    @Test
    @DisplayName("Linha órfã com recebimento é preservada")
    void upsert_linhaOrfaComRecebimento_preserva() {
        Fornecedor fornecedor = fornecedorPersistido("12345678000190", "Fornecedor");
        Pedido existente = pedidoPersistido("AL-009", fornecedor);
        Item orfao = itemTransiente("1", "MAT-MOVIMENTADO");
        orfao.setPedido(existente);
        orfao.setQuantidadeRecebida(new BigDecimal("1"));

        Pedido incoming = pedidoTransiente("AL-009", fornecedorTransiente(fornecedor.getCnpj(), fornecedor.getNome()),
                List.of());
        prepararPedidoExistente(incoming, existente);
        when(itemRepository.findByPedido(existente)).thenReturn(List.of(orfao));

        pedidoService.upsert(incoming);

        verify(itemRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Linha órfã referenciada em conferência é preservada")
    void upsert_linhaOrfaComHistorico_preserva() {
        Fornecedor fornecedor = fornecedorPersistido("12345678000190", "Fornecedor");
        Pedido existente = pedidoPersistido("AL-010", fornecedor);
        Item orfao = itemTransiente("1", "MAT-HISTORICO");
        orfao.setPedido(existente);
        orfao.setQuantidadeRecebida(BigDecimal.ZERO);

        Pedido incoming = pedidoTransiente("AL-010", fornecedorTransiente(fornecedor.getCnpj(), fornecedor.getNome()),
                List.of());
        prepararPedidoExistente(incoming, existente);
        when(itemRepository.findByPedido(existente)).thenReturn(List.of(orfao));
        when(divergenciaRepository.existsByConferencia_Pedido_IdAndCodigoMaterial(
                existente.getId(), "MAT-HISTORICO")).thenReturn(true);

        pedidoService.upsert(incoming);

        verify(itemRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Linha órfã sem recebimento é removida quando não há divergência daquele material")
    void upsert_linhaOrfaSemDivergenciaDoMaterial_remove() {
        Fornecedor fornecedor = fornecedorPersistido("12345678000190", "Fornecedor");
        Pedido existente = pedidoPersistido("AL-011", fornecedor);
        Item orfao = itemTransiente("1", "MAT-ORFAO");
        orfao.setPedido(existente);
        orfao.setQuantidadeRecebida(BigDecimal.ZERO);

        Pedido incoming = pedidoTransiente("AL-011", fornecedorTransiente(fornecedor.getCnpj(), fornecedor.getNome()),
                List.of());
        prepararPedidoExistente(incoming, existente);
        when(itemRepository.findByPedido(existente)).thenReturn(List.of(orfao));
        when(divergenciaRepository.existsByConferencia_Pedido_IdAndCodigoMaterial(
                existente.getId(), "MAT-ORFAO")).thenReturn(false);

        pedidoService.upsert(incoming);

        verify(itemRepository).delete(orfao);
    }

    private void prepararPedidoExistente(Pedido incoming, Pedido existente) {
        when(fornecedorRepository.findByCnpj(any())).thenReturn(Optional.of(existente.getFornecedor()));
        when(fornecedorRepository.save(any())).thenReturn(existente.getFornecedor());
        when(pedidoRepository.findByNumeroPedidoOrigemAndClienteOrigem(
                existente.getNumeroPedidoOrigem(), "ALFA")).thenReturn(Optional.of(existente));
        when(pedidoRepository.save(existente)).thenReturn(existente);
        when(pedidoRepository.findById(existente.getId())).thenReturn(Optional.of(existente));
    }
}
