package com.v360.prosel.conectorpedidoscompra.repository;

import com.v360.prosel.conectorpedidoscompra.entity.Divergencia;
import com.v360.prosel.conectorpedidoscompra.enums.TipoDivergencia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DivergenciaRepository extends JpaRepository<Divergencia, UUID> {
    long countByTipo(TipoDivergencia tipo);

    boolean existsByConferencia_Pedido_IdAndCodigoMaterial(UUID pedidoId, String codigoMaterial);
}
