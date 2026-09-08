package com.v360.prosel.conectorpedidoscompra.service;

import com.v360.prosel.conectorpedidoscompra.entity.Fornecedor;
import com.v360.prosel.conectorpedidoscompra.entity.Item;
import com.v360.prosel.conectorpedidoscompra.entity.Pedido;
import com.v360.prosel.conectorpedidoscompra.repository.DivergenciaRepository;
import com.v360.prosel.conectorpedidoscompra.repository.FornecedorRepository;
import com.v360.prosel.conectorpedidoscompra.repository.ItemRepository;
import com.v360.prosel.conectorpedidoscompra.repository.PedidoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Serviço responsável pelo upsert de pedidos de compra.
 *
 * Estratégia de upsert: find + save via JPA, não INSERT ... ON CONFLICT.
 * Decisão documentada em DESING.md seção 4.2.
 *
 * Chave de upsert: (numero_pedido_origem, cliente_origem) — DESING.md seção 3.5.
 */
@Service
@RequiredArgsConstructor
public class PedidoService {

    private final PedidoRepository pedidoRepository;
    private final FornecedorRepository fornecedorRepository;
    private final ItemRepository itemRepository;
    private final DivergenciaRepository divergenciaRepository;

    /**
     * Recebe um Pedido transiente (montado pelo Ingestor) e realiza o upsert completo:
     * 1. Resolve o Fornecedor (busca por CNPJ ou cria novo)
     * 2. Cria ou atualiza o Pedido
     * 3. Cria ou atualiza cada Item da lista
     *
     * @param incoming Pedido transiente com Fornecedor e Itens populados
     * @return Pedido persistido com IDs gerados
     */
    @Transactional
    public Pedido upsert(Pedido incoming) {
        List<Item> itens = new ArrayList<>(incoming.getItens()); // cópia defensiva antes de qualquer mutação
        Fornecedor fornecedor = resolverFornecedor(incoming.getFornecedor());
        Pedido pedido = resolverPedido(incoming, fornecedor);
        resolverItens(itens, pedido);
        removerOrfaos(pedido, linhasRecebidas(itens));
        return pedidoRepository.findById(pedido.getId()).orElse(pedido);
    }

    // -------------------------------------------------------------------------
    // Resolução de Fornecedor
    // -------------------------------------------------------------------------

    private Fornecedor resolverFornecedor(Fornecedor incoming) {
        return fornecedorRepository.findByCnpj(incoming.getCnpj())
                .map(existente -> atualizarFornecedor(existente, incoming))
                .orElseGet(() -> fornecedorRepository.save(incoming));
    }

    private Fornecedor atualizarFornecedor(Fornecedor existente, Fornecedor incoming) {
        existente.setNome(incoming.getNome());
        return fornecedorRepository.save(existente);
    }

    // -------------------------------------------------------------------------
    // Resolução de Pedido
    // -------------------------------------------------------------------------

    private Pedido resolverPedido(Pedido incoming, Fornecedor fornecedor) {
        return pedidoRepository
                .findByNumeroPedidoOrigemAndClienteOrigem(
                        incoming.getNumeroPedidoOrigem(),
                        incoming.getClienteOrigem()
                )
                .map(existente -> atualizarPedido(existente, incoming, fornecedor))
                .orElseGet(() -> criarPedido(incoming, fornecedor));
    }

    private Pedido criarPedido(Pedido incoming, Fornecedor fornecedor) {
        incoming.setFornecedor(fornecedor);
        incoming.setDataIngestao(Instant.now());
        incoming.getItens().clear(); // Itens são gerenciados separadamente em resolverItens
        return pedidoRepository.save(incoming);
    }

    private Pedido atualizarPedido(Pedido existente, Pedido incoming, Fornecedor fornecedor) {
        existente.setFornecedor(fornecedor);
        existente.setStatus(incoming.getStatus());
        existente.setDataCriacao(incoming.getDataCriacao());
        existente.setMoeda(incoming.getMoeda());
        existente.setDataIngestao(Instant.now());
        return pedidoRepository.save(existente);
    }

    // -------------------------------------------------------------------------
    // Resolução de Itens
    // -------------------------------------------------------------------------

    private void resolverItens(List<Item> itensEntrada, Pedido pedido) {
        for (Item incoming : itensEntrada) {
            itemRepository
                    .findByPedidoAndLinha(pedido, incoming.getLinha())
                    .ifPresentOrElse(
                            existente -> atualizarItem(existente, incoming),
                            () -> criarItem(incoming, pedido)
                    );
        }
    }

    private Set<String> linhasRecebidas(List<Item> itensEntrada) {
        return itensEntrada.stream()
                .map(Item::getLinha)
                .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * Remove somente linhas que desapareceram do payload e ainda não têm
     * movimentação ou histórico fiscal. O arquivo novo é a fonte de verdade
     * para linhas sem histórico; linhas movimentadas são preservadas.
     */
    private void removerOrfaos(Pedido pedido, Set<String> linhasRecebidas) {
        List<Item> persistidos = itemRepository.findByPedido(pedido);
        if (persistidos == null || persistidos.isEmpty()) {
            return;
        }

        for (Item item : persistidos) {
            if (linhasRecebidas.contains(item.getLinha())) {
                continue;
            }

            boolean possuiRecebimento = item.getQuantidadeRecebida() != null
                    && item.getQuantidadeRecebida().compareTo(java.math.BigDecimal.ZERO) > 0;
            boolean possuiDivergencia = pedido.getId() != null
                    && divergenciaRepository.existsByConferencia_Pedido_IdAndCodigoMaterial(
                    pedido.getId(), item.getCodigoMaterial());

            if (!possuiRecebimento && !possuiDivergencia) {
                itemRepository.delete(item);
            }
        }
    }

    private void criarItem(Item incoming, Pedido pedido) {
        incoming.setPedido(pedido);
        itemRepository.save(incoming);
    }

    private void atualizarItem(Item existente, Item incoming) {
        existente.setCodigoMaterial(incoming.getCodigoMaterial());
        existente.setDescricao(incoming.getDescricao());
        existente.setUnidadeMedida(incoming.getUnidadeMedida());
        existente.setQuantidadePedida(incoming.getQuantidadePedida());
        existente.setQuantidadeRecebida(incoming.getQuantidadeRecebida());
        existente.setPrecoUnitario(incoming.getPrecoUnitario());
        // quantidadePendente é GENERATED ALWAYS — o Postgres recalcula automaticamente
        itemRepository.save(existente);
    }
}
