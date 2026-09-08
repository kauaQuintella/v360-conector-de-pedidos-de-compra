package com.v360.prosel.conectorpedidoscompra.dto.conferencia;

import com.v360.prosel.conectorpedidoscompra.enums.ResultadoConferencia;

import java.util.List;
import java.util.UUID;

public record ConferenciaResponseDTO(
        UUID idConferencia,
        UUID idPedido,
        ResultadoConferencia resultado,
        List<DivergenciaDTO> divergencias
) {
}
