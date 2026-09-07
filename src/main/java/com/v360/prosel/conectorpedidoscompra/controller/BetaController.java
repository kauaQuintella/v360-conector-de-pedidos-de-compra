package com.v360.prosel.conectorpedidoscompra.controller;

import com.v360.prosel.conectorpedidoscompra.dto.beta.BetaPedidoDTO;
import com.v360.prosel.conectorpedidoscompra.entity.Pedido;
import com.v360.prosel.conectorpedidoscompra.ingestor.BetaIngestor;
import com.v360.prosel.conectorpedidoscompra.parser.BetaCsvParser;
import com.v360.prosel.conectorpedidoscompra.service.PedidoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;

/**
 * Ingestão de pedidos do cliente Beta (dois CSVs).
 */
@RestController
@RequestMapping("/ingest")
@RequiredArgsConstructor
public class BetaController {

    private final BetaCsvParser betaCsvParser;
    private final BetaIngestor betaIngestor;
    private final PedidoService pedidoService;

    @PostMapping(path = "/beta", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.OK)
    public List<Map<String, Object>> ingestirBeta(
            @RequestParam("cabecalho") MultipartFile cabecalho,
            @RequestParam("itens") MultipartFile itens
    ) {
        List<BetaPedidoDTO> pedidos = parse(cabecalho, itens);
        return pedidos.stream()
                .map(dto -> {
                    Pedido pedido = betaIngestor.toEntity(dto);
                    Pedido salvo = pedidoService.upsert(pedido);
                    return Map.<String, Object>of(
                            "id_pedido", salvo.getId(),
                            "numero_pedido_origem", salvo.getNumeroPedidoOrigem(),
                            "cliente_origem", salvo.getClienteOrigem(),
                            "status", salvo.getStatus()
                    );
                })
                .toList();
    }

    private List<BetaPedidoDTO> parse(MultipartFile cabecalho, MultipartFile itens) {
        try {
            return betaCsvParser.parse(cabecalho.getInputStream(), itens.getInputStream());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
