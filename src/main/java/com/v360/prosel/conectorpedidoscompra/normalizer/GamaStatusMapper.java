package com.v360.prosel.conectorpedidoscompra.normalizer;

import com.v360.prosel.conectorpedidoscompra.enums.StatusPedido;
import org.springframework.stereotype.Component;

/**
 * Traduz a situação numérica do Gama (1/2/3) para {@link StatusPedido}.
 * Não implementa {@link StatusMapper} ({@code map(String)}): o Gama envia Integer.
 * Alterar a interface compartilhada tocaria Alfa e Beta.
 */
@Component
public class GamaStatusMapper {

    public StatusPedido map(Integer situacao) {
        if (situacao == null) {
            throw new IllegalArgumentException("Situação Gama não pode ser nula");
        }
        return switch (situacao) {
            case 1 -> StatusPedido.OPEN;
            case 2 -> StatusPedido.CLOSED;
            case 3 -> StatusPedido.BLOCKED;
            default -> throw new IllegalArgumentException("Situação Gama desconhecida: " + situacao);
        };
    }
}
