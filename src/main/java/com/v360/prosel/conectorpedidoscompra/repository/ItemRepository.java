package com.v360.prosel.conectorpedidoscompra.repository;

import com.v360.prosel.conectorpedidoscompra.entity.Item;
import com.v360.prosel.conectorpedidoscompra.entity.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ItemRepository extends JpaRepository<Item, UUID> {

    /** Busca item pela chave única (id_pedido, linha). */
    Optional<Item> findByPedidoAndLinha(Pedido pedido, String linha);

    List<Item> findByPedido(Pedido pedido);
}
