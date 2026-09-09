package com.v360.prosel.conectorpedidoscompra.normalizer;

import java.time.Instant;

/**
 * Converte {@code dt_criacao} do Gama (epoch em segundos) para {@link Instant}.
 * Isolado, sem interface genérica — formatos de data dos clientes são incompatíveis.
 */
public final class GamaDateParser {

    private GamaDateParser() {
    }

    public static Instant parse(Long epochSegundos) {
        if (epochSegundos == null) {
            return null;
        }
        return Instant.ofEpochSecond(epochSegundos);
    }
}
