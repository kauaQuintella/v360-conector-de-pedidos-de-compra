package com.v360.prosel.conectorpedidoscompra.controller;

import com.v360.prosel.conectorpedidoscompra.dto.conferencia.ConferenciaResponseDTO;
import com.v360.prosel.conectorpedidoscompra.dto.conferencia.NotaFiscalDTO;
import com.v360.prosel.conectorpedidoscompra.dto.conferencia.RelatorioConferenciasDTO;
import com.v360.prosel.conectorpedidoscompra.service.ConferenciaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ConferenciaController {
    private final ConferenciaService conferenciaService;

    @PostMapping("/notas-fiscais/conferir")
    public ConferenciaResponseDTO conferir(@Valid @RequestBody NotaFiscalDTO nota) {
        return conferenciaService.conferir(nota);
    }

    @GetMapping("/relatorios/conferencias")
    public RelatorioConferenciasDTO relatorio() {
        return conferenciaService.relatorio();
    }
}
