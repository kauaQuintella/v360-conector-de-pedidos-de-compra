package com.v360.prosel.conectorpedidoscompra.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Fornecedor único no ecossistema V360.
 * CNPJ tem constraint UNIQUE global: um fornecedor que aparece em dois clientes
 * diferentes é tratado como a mesma entidade (DESING.md seção 3.2).
 */
@Entity
@Table(name = "Fornecedor")
@Getter
@Setter
@NoArgsConstructor
public class Fornecedor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_fornecedor")
    private UUID id;

    /** CNPJ sempre sem máscara (só dígitos) — sanitizado pelo CnpjSanitizer (DESING.md seção 2). */
    @Column(name = "cnpj", unique = true, nullable = false)
    private String cnpj;

    @Column(name = "nome")
    private String nome;
}
