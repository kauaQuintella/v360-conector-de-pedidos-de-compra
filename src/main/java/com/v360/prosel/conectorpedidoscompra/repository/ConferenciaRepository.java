package com.v360.prosel.conectorpedidoscompra.repository;

import com.v360.prosel.conectorpedidoscompra.entity.Conferencia;
import com.v360.prosel.conectorpedidoscompra.enums.ResultadoConferencia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ConferenciaRepository extends JpaRepository<Conferencia, UUID> {
    long countByResultado(ResultadoConferencia resultado);
}
