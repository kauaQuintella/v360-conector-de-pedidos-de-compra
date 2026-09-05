package com.v360.prosel.conectorpedidoscompra.service;

import com.v360.prosel.conectorpedidoscompra.entity.Fornecedor;
import com.v360.prosel.conectorpedidoscompra.entity.Item;
import com.v360.prosel.conectorpedidoscompra.entity.Pedido;
import com.v360.prosel.conectorpedidoscompra.repository.FornecedorRepository;
import com.v360.prosel.conectorpedidoscompra.repository.ItemRepository;
import com.v360.prosel.conectorpedidoscompra.repository.PedidoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

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
        Fornecedor fornecedor = resolverFornecedor(incoming.getFornecedor());
        Pedido pedido = resolverPedido(incoming, fornecedor);
        resolverItens(incoming.getItens(), pedido);
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
        incoming.getItens().clear(); // itens são gerenciados separadamente em resolverItens
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
