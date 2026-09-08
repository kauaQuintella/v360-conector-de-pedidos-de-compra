package com.v360.prosel.conectorpedidoscompra.controller;

import com.v360.prosel.conectorpedidoscompra.dto.pedido.PedidoResumoResponseDTO;
import com.v360.prosel.conectorpedidoscompra.dto.pedido.PedidoResponseDTO;
import com.v360.prosel.conectorpedidoscompra.enums.StatusPedido;
import com.v360.prosel.conectorpedidoscompra.service.PedidoConsultaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/pedidos")
@RequiredArgsConstructor
public class PedidoController {
    private final PedidoConsultaService pedidoConsultaService;

    @GetMapping
    public List<PedidoResumoResponseDTO> listar(
            @RequestParam(name = "cliente_origem", required = false) String clienteOrigem,
            @RequestParam(name = "fornecedor", required = false) String fornecedor,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "com_pendencia", required = false) Boolean comPendencia) {
        return pedidoConsultaService.consultar(clienteOrigem, fornecedor, parseStatus(status), comPendencia);
    }

    @GetMapping("/{id}")
    public PedidoResponseDTO detalhar(@PathVariable UUID id) {
        return pedidoConsultaService.consultarPorId(id);
    }

    private StatusPedido parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return StatusPedido.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status inválido");
        }
    }
}
