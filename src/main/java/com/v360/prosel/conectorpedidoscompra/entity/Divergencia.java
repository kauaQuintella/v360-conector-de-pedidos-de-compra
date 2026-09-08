package com.v360.prosel.conectorpedidoscompra.entity;

import com.v360.prosel.conectorpedidoscompra.enums.TipoDivergencia;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "Divergencia")
@Getter
@Setter
@NoArgsConstructor
public class Divergencia {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_divergencia")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_conferencia", nullable = false)
    private Conferencia conferencia;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", length = 50)
    private TipoDivergencia tipo;

    @Column(name = "codigo_material", length = 100)
    private String codigoMaterial;

    @Column(name = "valor_esperado", columnDefinition = "TEXT")
    private String valorEsperado;

    @Column(name = "valor_recebido", columnDefinition = "TEXT")
    private String valorRecebido;

    @Column(name = "descricao", columnDefinition = "TEXT")
    private String descricao;
}
