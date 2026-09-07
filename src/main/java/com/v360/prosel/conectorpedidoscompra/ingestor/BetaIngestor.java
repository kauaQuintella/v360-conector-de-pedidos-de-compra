package com.v360.prosel.conectorpedidoscompra.ingestor;

import com.v360.prosel.conectorpedidoscompra.dto.beta.BetaItemDTO;
import com.v360.prosel.conectorpedidoscompra.dto.beta.BetaPedidoDTO;
import com.v360.prosel.conectorpedidoscompra.entity.Fornecedor;
import com.v360.prosel.conectorpedidoscompra.entity.Item;
import com.v360.prosel.conectorpedidoscompra.entity.Pedido;
import com.v360.prosel.conectorpedidoscompra.ingestor.strategy.PedidoIngestor;
import com.v360.prosel.conectorpedidoscompra.normalizer.BetaDateParser;
import com.v360.prosel.conectorpedidoscompra.normalizer.BetaNumberParser;
import com.v360.prosel.conectorpedidoscompra.normalizer.BetaStatusMapper;
import com.v360.prosel.conectorpedidoscompra.normalizer.CnpjSanitizer;
import com.v360.prosel.conectorpedidoscompra.normalizer.StringSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Ingestor do cliente Beta (CSV). Traduz {@link BetaPedidoDTO} → {@link Pedido} transiente.
 * Persistência fica no {@code PedidoService}.
 */
@Component
@RequiredArgsConstructor
public class BetaIngestor implements PedidoIngestor<BetaPedidoDTO> {

    private static final String CLIENTE_ORIGEM = "BETA";

    private final BetaStatusMapper statusMapper;

    @Override
    public Pedido toEntity(BetaPedidoDTO dto) {
        Fornecedor fornecedor = mapFornecedor(dto);
        Pedido pedido = mapPedido(dto, fornecedor);
        List<Item> itens = mapItens(dto, pedido);
        pedido.setItens(itens);
        return pedido;
    }

    private Fornecedor mapFornecedor(BetaPedidoDTO dto) {
        Fornecedor fornecedor = new Fornecedor();
        fornecedor.setCnpj(CnpjSanitizer.sanitize(dto.fornecedorCnpj()));
        fornecedor.setNome(StringSanitizer.sanitize(dto.fornecedorRazaoSocial()));
        return fornecedor;
    }

    private Pedido mapPedido(BetaPedidoDTO dto, Fornecedor fornecedor) {
        Pedido pedido = new Pedido();
        pedido.setNumeroPedidoOrigem(dto.numeroPedido());
        pedido.setClienteOrigem(CLIENTE_ORIGEM);
        pedido.setDataCriacao(BetaDateParser.parse(dto.emissao()));
        pedido.setStatus(statusMapper.map(dto.situacao()));
        pedido.setMoeda(dto.moeda());
        pedido.setFornecedor(fornecedor);
        return pedido;
    }

    private List<Item> mapItens(BetaPedidoDTO dto, Pedido pedido) {
        return dto.itens().stream()
                .map(itemDTO -> mapItem(itemDTO, pedido))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private Item mapItem(BetaItemDTO dto, Pedido pedido) {
        Item item = new Item();
        item.setPedido(pedido);
        item.setLinha(dto.linha());
        item.setCodigoMaterial(dto.codigoMaterial());
        item.setDescricao(StringSanitizer.sanitize(dto.descricao()));
        item.setUnidadeMedida(dto.unidade());
        item.setQuantidadePedida(BetaNumberParser.parse(dto.qtdPedida()));
        item.setQuantidadeRecebida(BetaNumberParser.parse(dto.qtdRecebida()));
        item.setPrecoUnitario(BetaNumberParser.parse(dto.precoUnitario()));
        return item;
    }
}
