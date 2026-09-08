package com.v360.prosel.conectorpedidoscompra.service;

import com.v360.prosel.conectorpedidoscompra.dto.pedido.FornecedorResponseDTO;
import com.v360.prosel.conectorpedidoscompra.dto.pedido.ItemResponseDTO;
import com.v360.prosel.conectorpedidoscompra.dto.pedido.PedidoResumoResponseDTO;
import com.v360.prosel.conectorpedidoscompra.dto.pedido.PedidoResponseDTO;
import com.v360.prosel.conectorpedidoscompra.entity.Item;
import com.v360.prosel.conectorpedidoscompra.entity.Pedido;
import com.v360.prosel.conectorpedidoscompra.enums.StatusPedido;
import com.v360.prosel.conectorpedidoscompra.normalizer.CnpjSanitizer;
import com.v360.prosel.conectorpedidoscompra.repository.PedidoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PedidoConsultaService {
    private final PedidoRepository pedidoRepository;

    @Transactional(readOnly = true)
    public List<PedidoResumoResponseDTO> consultar(String clienteOrigem, String fornecedor,
                                                   StatusPedido status, Boolean comPendencia) {
        String cliente = blankToNull(clienteOrigem);
        String cnpj = sanitizarCnpj(fornecedor);
        List<Pedido> pedidos;
        if (comPendencia == null) {
            pedidos = pedidoRepository.findResumoByFiltros(cliente, cnpj, status);
        } else if (comPendencia) {
            pedidos = pedidoRepository.findResumoByFiltrosComPendencia(cliente, cnpj, status);
        } else {
            pedidos = pedidoRepository.findResumoByFiltrosSemPendencia(cliente, cnpj, status);
        }
        return pedidos.stream().map(this::toResumo).toList();
    }

    @Transactional(readOnly = true)
    public PedidoResponseDTO consultarPorId(UUID id) {
        return pedidoRepository.findDetalheById(id)
                .map(this::toDetalhe)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "pedido não encontrado"));
    }

    private PedidoResumoResponseDTO toResumo(Pedido pedido) {
        return new PedidoResumoResponseDTO(
                pedido.getId(),
                pedido.getNumeroPedidoOrigem(),
                pedido.getClienteOrigem(),
                pedido.getStatus(),
                pedido.getDataCriacao(),
                pedido.getMoeda(),
                toFornecedor(pedido)
        );
    }

    private PedidoResponseDTO toDetalhe(Pedido pedido) {
        List<ItemResponseDTO> itens = pedido.getItens() == null ? List.of() :
                pedido.getItens().stream().map(this::toItem).toList();
        return new PedidoResponseDTO(
                pedido.getId(),
                pedido.getNumeroPedidoOrigem(),
                pedido.getClienteOrigem(),
                pedido.getStatus(),
                pedido.getDataCriacao(),
                pedido.getMoeda(),
                toFornecedor(pedido),
                itens
        );
    }

    private FornecedorResponseDTO toFornecedor(Pedido pedido) {
        if (pedido.getFornecedor() == null) {
            return null;
        }
        return new FornecedorResponseDTO(
                pedido.getFornecedor().getId(),
                pedido.getFornecedor().getCnpj(),
                pedido.getFornecedor().getNome()
        );
    }

    private ItemResponseDTO toItem(Item item) {
        return new ItemResponseDTO(
                item.getId(),
                item.getLinha(),
                item.getCodigoMaterial(),
                item.getDescricao(),
                item.getUnidadeMedida(),
                item.getQuantidadePedida(),
                item.getQuantidadeRecebida(),
                item.getQuantidadePendente(),
                item.getPrecoUnitario()
        );
    }

    private String sanitizarCnpj(String fornecedor) {
        String bruto = blankToNull(fornecedor);
        if (bruto == null) {
            return null;
        }
        String sanitizado = CnpjSanitizer.sanitize(bruto);
        return blankToNull(sanitizado);
    }

    private String blankToNull(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.trim();
    }
}
