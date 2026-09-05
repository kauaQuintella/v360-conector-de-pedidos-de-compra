package com.v360.prosel.conectorpedidoscompra.repository;

import com.v360.prosel.conectorpedidoscompra.entity.Fornecedor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FornecedorRepository extends JpaRepository<Fornecedor, UUID> {

    /** Busca por CNPJ já sanitizado (só dígitos). */
    Optional<Fornecedor> findByCnpj(String cnpj);
}
