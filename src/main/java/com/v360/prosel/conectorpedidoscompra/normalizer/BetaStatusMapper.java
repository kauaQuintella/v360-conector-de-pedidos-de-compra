package com.v360.prosel.conectorpedidoscompra.normalizer;

import com.v360.prosel.conectorpedidoscompra.enums.StatusPedido;
import org.springframework.stereotype.Component;

/**
 * StatusMapper do Beta Alimentos (vocabulário pt-BR no CSV).
 */
@Component
public class BetaStatusMapper implements StatusMapper {

    @Override
    public StatusPedido map(String rawStatus) {
        if (rawStatus == null) {
            throw new IllegalArgumentException("Status não pode ser nulo");
        }
        return switch (rawStatus.toUpperCase().trim()) {
            case "EM ABERTO" -> StatusPedido.OPEN;
            case "ENCERRADO" -> StatusPedido.CLOSED;
            case "BLOQUEADO" -> StatusPedido.BLOCKED;
            default -> throw new IllegalArgumentException(
                    "Status do Beta não reconhecido: '" + rawStatus + "'"
            );
        };
    }
}
