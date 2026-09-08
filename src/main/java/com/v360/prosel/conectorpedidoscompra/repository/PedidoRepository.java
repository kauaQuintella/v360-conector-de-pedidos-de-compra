package com.v360.prosel.conectorpedidoscompra.repository;

import com.v360.prosel.conectorpedidoscompra.entity.Pedido;
import com.v360.prosel.conectorpedidoscompra.enums.StatusPedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PedidoRepository extends JpaRepository<Pedido, UUID> {

    /**
     * Chave de upsert do sistema (DESING.md seção 3.5).
     * Identifica unicamente um pedido dentro de um cliente de origem.
     */
    Optional<Pedido> findByNumeroPedidoOrigemAndClienteOrigem(
            String numeroPedidoOrigem,
            String clienteOrigem
    );

    @Query("""
            select distinct p from Pedido p
            left join fetch p.fornecedor f
            where (:clienteOrigem is null or p.clienteOrigem = :clienteOrigem)
              and (:cnpjFornecedor is null or f.cnpj = :cnpjFornecedor)
              and (:status is null or p.status = :status)
            """)
    List<Pedido> findResumoByFiltros(
            @Param("clienteOrigem") String clienteOrigem,
            @Param("cnpjFornecedor") String cnpjFornecedor,
            @Param("status") StatusPedido status
    );

    @Query("""
            select distinct p from Pedido p
            left join fetch p.fornecedor f
            where (:clienteOrigem is null or p.clienteOrigem = :clienteOrigem)
              and (:cnpjFornecedor is null or f.cnpj = :cnpjFornecedor)
              and (:status is null or p.status = :status)
              and exists (select 1 from Item pi
                          where pi.pedido = p
                            and pi.quantidadePendente > 0)
            """)
    List<Pedido> findResumoByFiltrosComPendencia(
            @Param("clienteOrigem") String clienteOrigem,
            @Param("cnpjFornecedor") String cnpjFornecedor,
            @Param("status") StatusPedido status
    );

    @Query("""
            select distinct p from Pedido p
            left join fetch p.fornecedor f
            where (:clienteOrigem is null or p.clienteOrigem = :clienteOrigem)
              and (:cnpjFornecedor is null or f.cnpj = :cnpjFornecedor)
              and (:status is null or p.status = :status)
              and not exists (select 1 from Item pi
                              where pi.pedido = p
                                and pi.quantidadePendente > 0)
            """)
    List<Pedido> findResumoByFiltrosSemPendencia(
            @Param("clienteOrigem") String clienteOrigem,
            @Param("cnpjFornecedor") String cnpjFornecedor,
            @Param("status") StatusPedido status
    );

    @Query("""
            select p from Pedido p
            left join fetch p.fornecedor
            left join fetch p.itens
            where p.id = :id
            """)
    Optional<Pedido> findDetalheById(@Param("id") UUID id);
}
