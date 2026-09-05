package com.v360.prosel.conectorpedidoscompra.controller;

import com.v360.prosel.conectorpedidoscompra.dto.alfa.AlfaPedidoDTO;
import com.v360.prosel.conectorpedidoscompra.entity.Pedido;
import com.v360.prosel.conectorpedidoscompra.ingestor.AlfaIngestor;
import com.v360.prosel.conectorpedidoscompra.service.PedidoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Endpoint de ingestão de pedidos do cliente Alfa.
 *
 * Rota dedicada por cliente — o Spring resolve o AlfaIngestor via injeção de dependência,
 * tornando o Factory formal desnecessário neste desenho (DESING.md seção 1.3).
 */
@RestController
@RequestMapping("/ingest")
@RequiredArgsConstructor
public class AlfaController {

    private final AlfaIngestor alfaIngestor;
    private final PedidoService pedidoService;

    /**
     * Recebe um pedido de compra do cliente Alfa no formato JSON aninhado,
     * traduz via AlfaIngestor e persiste via PedidoService (upsert).
     *
     * @param dto payload validado pelo Bean Validation
     * @return id do pedido persistido e mensagem de confirmação
     */
    @PostMapping("/alfa")
    @ResponseStatus(HttpStatus.OK)
    public Map<String, Object> ingestirAlfa(@RequestBody @Valid AlfaPedidoDTO dto) {
        Pedido pedido = alfaIngestor.toEntity(dto);
        Pedido salvo = pedidoService.upsert(pedido);
        return Map.of(
                "id_pedido", salvo.getId(),
                "numero_pedido_origem", salvo.getNumeroPedidoOrigem(),
                "cliente_origem", salvo.getClienteOrigem(),
                "status", salvo.getStatus()
        );
    }
}
