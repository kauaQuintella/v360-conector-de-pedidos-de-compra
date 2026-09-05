package com.v360.prosel.conectorpedidoscompra.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Item de um pedido de compra.
 *
 * quantidade_pendente é GENERATED ALWAYS AS (quantidade_pedida - quantidade_recebida) STORED
 * no Postgres. O JPA nunca escreve nessa coluna — insertable=false, updatable=false
 * é obrigatório; tentar gravar numa coluna GENERATED ALWAYS causa erro no banco (DESING.md 3.4).
 *
 * Chave única: (id_pedido, linha) — DESING.md seção 3.
 */
@Entity
@Table(
        name = "Item",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_item_pedido",
                columnNames = {"id_pedido", "linha"}
        )
)
@Getter
@Setter
@NoArgsConstructor
public class Item {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_item")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_pedido")
    private Pedido pedido;

    @Column(name = "linha")
    private String linha;

    @Column(name = "codigo_material")
    private String codigoMaterial;

    @Column(name = "descricao", columnDefinition = "TEXT")
    private String descricao;

    @Column(name = "unidade_medida")
    private String unidadeMedida;

    /** NUMERIC(15,4) — evita erro de arredondamento de ponto flutuante (DESING.md 3.3). */
    @Column(name = "quantidade_pedida", precision = 15, scale = 4)
    private BigDecimal quantidadePedida;

    @Column(name = "quantidade_recebida", precision = 15, scale = 4)
    private BigDecimal quantidadeRecebida;

    /**
     * Coluna gerada pelo Postgres. O JPA apenas lê este campo.
     * insertable=false e updatable=false são obrigatórios (DESING.md 3.4).
     */
    @Column(name = "quantidade_pendente", insertable = false, updatable = false, precision = 15, scale = 4)
    private BigDecimal quantidadePendente;

    @Column(name = "preco_unitario", precision = 15, scale = 4)
    private BigDecimal precoUnitario;
}
