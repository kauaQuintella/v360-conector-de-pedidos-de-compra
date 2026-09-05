package com.v360.prosel.conectorpedidoscompra.entity;

import com.v360.prosel.conectorpedidoscompra.enums.StatusPedido;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Pedido de compra normalizado no contrato V360.
 *
 * Chave de upsert: (numero_pedido_origem, cliente_origem) — DESING.md seção 3.5.
 * cliente_origem identifica de qual cliente veio o pedido (ALFA, BETA, GAMA).
 */
@Entity
@Table(
        name = "Pedido",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_pedido_origem",
                columnNames = {"numero_pedido_origem", "cliente_origem"}
        )
)
@Getter
@Setter
@NoArgsConstructor
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_pedido")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_fornecedor")
    private Fornecedor fornecedor;

    @Column(name = "numero_pedido_origem")
    private String numeroPedidoOrigem;

    /**
     * Identifica o sistema de origem (ex.: "ALFA", "BETA", "GAMA").
     * Metade da chave de upsert — garante que o mesmo número de pedido
     * de dois clientes diferentes não seja tratado como o mesmo pedido.
     */
    @Column(name = "cliente_origem")
    private String clienteOrigem;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private StatusPedido status;

    @Column(name = "data_criacao")
    private Instant dataCriacao;

    /** Preenchido pelo PedidoService no momento da ingestão. */
    @Column(name = "data_ingestao")
    private Instant dataIngestao;

    @Column(name = "moeda", length = 3)
    private String moeda;

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Item> itens = new ArrayList<>();
}
