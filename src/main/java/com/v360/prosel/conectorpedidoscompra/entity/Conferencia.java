package com.v360.prosel.conectorpedidoscompra.entity;

import com.v360.prosel.conectorpedidoscompra.enums.ResultadoConferencia;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "Conferencia")
@Getter
@Setter
@NoArgsConstructor
public class Conferencia {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_conferencia")
    private UUID id;

    /**
     * A associação é opcional porque uma nota pode ser rejeitada antes de
     * qualquer pedido ser encontrado.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_pedido")
    private Pedido pedido;

    @Column(name = "fornecedor_cnpj", length = 20)
    private String fornecedorCnpj;

    @Enumerated(EnumType.STRING)
    @Column(name = "resultado", length = 20)
    private ResultadoConferencia resultado;

    @Column(name = "data_conferencia")
    private Instant dataConferencia;

    @OneToMany(mappedBy = "conferencia", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Divergencia> divergencias = new ArrayList<>();

    public void adicionarDivergencia(Divergencia divergencia) {
        divergencias.add(divergencia);
        divergencia.setConferencia(this);
    }
}
