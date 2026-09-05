package com.v360.prosel.conectorpedidoscompra.normalizer;

import com.v360.prosel.conectorpedidoscompra.enums.StatusPedido;
import org.springframework.stereotype.Component;

/**
 * StatusMapper para o cliente Alfa.
 * O Alfa já envia o status em inglês ("OPEN", "CLOSED", "BLOCKED"),
 * compatível com o enum V360 — a tradução é case-insensitive por segurança.
 * (DESING.md seção 6)
 */
@Component
public class AlfaStatusMapper implements StatusMapper {

    @Override
    public StatusPedido map(String rawStatus) {
        if (rawStatus == null) {
            throw new IllegalArgumentException("Status não pode ser nulo");
        }
        return switch (rawStatus.toUpperCase().trim()) {
            case "OPEN"    -> StatusPedido.OPEN;
            case "CLOSED"  -> StatusPedido.CLOSED;
            case "BLOCKED" -> StatusPedido.BLOCKED;
            default -> throw new IllegalArgumentException(
                    "Status do Alfa não reconhecido: '" + rawStatus + "'"
            );
        };
    }
}
