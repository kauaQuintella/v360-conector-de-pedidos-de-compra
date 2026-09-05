package com.v360.prosel.conectorpedidoscompra.repository;

import com.v360.prosel.conectorpedidoscompra.entity.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
