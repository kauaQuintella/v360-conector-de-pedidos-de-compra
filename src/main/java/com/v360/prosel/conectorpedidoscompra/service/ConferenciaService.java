package com.v360.prosel.conectorpedidoscompra.service;

import com.v360.prosel.conectorpedidoscompra.dto.conferencia.ConferenciaResponseDTO;
import com.v360.prosel.conectorpedidoscompra.dto.conferencia.DivergenciaDTO;
import com.v360.prosel.conectorpedidoscompra.dto.conferencia.NotaFiscalDTO;
import com.v360.prosel.conectorpedidoscompra.dto.conferencia.NotaFiscalItemDTO;
import com.v360.prosel.conectorpedidoscompra.dto.conferencia.RelatorioConferenciasDTO;
import com.v360.prosel.conectorpedidoscompra.entity.Conferencia;
import com.v360.prosel.conectorpedidoscompra.entity.Divergencia;
import com.v360.prosel.conectorpedidoscompra.entity.Item;
import com.v360.prosel.conectorpedidoscompra.entity.Pedido;
import com.v360.prosel.conectorpedidoscompra.enums.ResultadoConferencia;
import com.v360.prosel.conectorpedidoscompra.enums.StatusPedido;
import com.v360.prosel.conectorpedidoscompra.enums.TipoDivergencia;
import com.v360.prosel.conectorpedidoscompra.normalizer.CnpjSanitizer;
import com.v360.prosel.conectorpedidoscompra.repository.ConferenciaRepository;
import com.v360.prosel.conectorpedidoscompra.repository.DivergenciaRepository;
import com.v360.prosel.conectorpedidoscompra.repository.PedidoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ConferenciaService {

    static final BigDecimal TOLERANCIA_VALOR = new BigDecimal("0.05");

    private final PedidoRepository pedidoRepository;
    private final ConferenciaRepository conferenciaRepository;
    private final DivergenciaRepository divergenciaRepository;

    @Transactional
    public ConferenciaResponseDTO conferir(NotaFiscalDTO nota) {
        String cnpjNota = CnpjSanitizer.sanitize(nota.getFornecedorCnpj());
        List<Divergencia> falhas = new ArrayList<>();

        Pedido pedido = pedidoRepository
                .findByNumeroPedidoOrigemAndClienteOrigem(nota.getNumeroPedido(), nota.getClienteOrigem())
                .orElse(null);

        if (pedido == null) {
            falhas.add(novaDivergencia(
                    TipoDivergencia.PEDIDO_NAO_ENCONTRADO,
                    null,
                    nota.getNumeroPedido() + "/" + nota.getClienteOrigem(),
                    nota.getNumeroPedido(),
                    "Pedido não encontrado pela chave (numero_pedido, cliente_origem)."
            ));
            Conferencia salva = persistir(null, cnpjNota, falhas);
            return toResponse(salva);
        }

        conferirFornecedor(pedido, cnpjNota, falhas);
        conferirStatus(pedido, falhas);
        conferirItens(pedido, nota.getItens(), falhas);

        Conferencia salva = persistir(pedido, cnpjNota, falhas);
        return toResponse(salva);
    }

    @Transactional(readOnly = true)
    public RelatorioConferenciasDTO relatorio() {
        Map<String, Long> porResultado = new LinkedHashMap<>();
        for (ResultadoConferencia resultado : ResultadoConferencia.values()) {
            porResultado.put(resultado.name(), conferenciaRepository.countByResultado(resultado));
        }

        Map<String, Long> porTipo = new LinkedHashMap<>();
        for (TipoDivergencia tipo : TipoDivergencia.values()) {
            porTipo.put(tipo.name(), divergenciaRepository.countByTipo(tipo));
        }

        long total = 0;
        for (Long qtd : porResultado.values()) {
            total += qtd;
        }
        return new RelatorioConferenciasDTO(total, porResultado, porTipo);
    }

    private void conferirFornecedor(Pedido pedido, String cnpjNota, List<Divergencia> falhas) {
        String cnpjPedido = pedido.getFornecedor() == null ? null : pedido.getFornecedor().getCnpj();
        if (cnpjPedido == null || !cnpjPedido.equals(cnpjNota)) {
            falhas.add(novaDivergencia(
                    TipoDivergencia.FORNECEDOR_DIVERGENTE,
                    null,
                    cnpjPedido,
                    cnpjNota,
                    "CNPJ da nota diverge do CNPJ do pedido."
            ));
        }
    }

    private void conferirStatus(Pedido pedido, List<Divergencia> falhas) {
        if (pedido.getStatus() == StatusPedido.BLOCKED) {
            falhas.add(novaDivergencia(
                    TipoDivergencia.PEDIDO_BLOQUEADO,
                    null,
                    StatusPedido.OPEN.name(),
                    StatusPedido.BLOCKED.name(),
                    "Pedido está BLOCKED; a avaliação dos itens continua."
            ));
        } else if (pedido.getStatus() == StatusPedido.CLOSED) {
            falhas.add(novaDivergencia(
                    TipoDivergencia.PEDIDO_ENCERRADO,
                    null,
                    StatusPedido.OPEN.name(),
                    StatusPedido.CLOSED.name(),
                    "Pedido está CLOSED; a avaliação dos itens continua."
            ));
        }
    }

    private void conferirItens(Pedido pedido, List<NotaFiscalItemDTO> itensNota, List<Divergencia> falhas) {
        Map<String, List<Item>> itensPorMaterial = pedido.getItens() == null ? Map.of() :
                pedido.getItens().stream().collect(Collectors.groupingBy(Item::getCodigoMaterial));

        Map<String, AgregadoNota> notaPorMaterial = new LinkedHashMap<>();
        for (NotaFiscalItemDTO itemNota : itensNota) {
            notaPorMaterial.merge(
                    itemNota.getCodigoMaterial(),
                    new AgregadoNota(nvl(itemNota.getQuantidade()), nvl(itemNota.getValorTotal())),
                    AgregadoNota::somar
            );
        }

        for (Map.Entry<String, AgregadoNota> entrada : notaPorMaterial.entrySet()) {
            String material = entrada.getKey();
            AgregadoNota agregadoNota = entrada.getValue();
            List<Item> linhasPedido = itensPorMaterial.get(material);
            if (linhasPedido == null || linhasPedido.isEmpty()) {
                falhas.add(novaDivergencia(
                        TipoDivergencia.MATERIAL_NAO_ENCONTRADO,
                        material,
                        null,
                        material,
                        "Material da nota não existe no pedido."
                ));
                continue;
            }

            BigDecimal pendenteAgregado = linhasPedido.stream()
                    .map(item -> nvl(item.getQuantidadePendente()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (agregadoNota.quantidade().compareTo(pendenteAgregado) > 0) {
                falhas.add(novaDivergencia(
                        TipoDivergencia.QUANTIDADE_EXCEDE_PENDENTE,
                        material,
                        pendenteAgregado.toPlainString(),
                        agregadoNota.quantidade().toPlainString(),
                        "Quantidade da nota excede a soma da quantidade_pendente do material."
                ));
            }

            BigDecimal precoRef = precoPonderado(linhasPedido);
            BigDecimal valorEsperado = agregadoNota.quantidade().multiply(precoRef);
            if (agregadoNota.valorTotal().subtract(valorEsperado).abs().compareTo(TOLERANCIA_VALOR) > 0) {
                falhas.add(novaDivergencia(
                        TipoDivergencia.VALOR_DIVERGENTE,
                        material,
                        valorEsperado.stripTrailingZeros().toPlainString(),
                        agregadoNota.valorTotal().toPlainString(),
                        "Diferença absoluta entre valor da nota e quantidade × preço de referência maior que R$ 0,05."
                ));
            }
        }
    }

    private BigDecimal precoPonderado(List<Item> linhas) {
        BigDecimal somaPendente = BigDecimal.ZERO;
        BigDecimal somaPonderada = BigDecimal.ZERO;
        BigDecimal primeiroPreco = null;
        boolean precosIguais = true;
        BigDecimal somaPrecos = BigDecimal.ZERO;
        int qtdLinhas = 0;

        for (Item item : linhas) {
            BigDecimal preco = nvl(item.getPrecoUnitario());
            BigDecimal pendente = nvl(item.getQuantidadePendente());
            if (primeiroPreco == null) {
                primeiroPreco = preco;
            } else if (primeiroPreco.compareTo(preco) != 0) {
                precosIguais = false;
            }
            somaPendente = somaPendente.add(pendente);
            somaPonderada = somaPonderada.add(pendente.multiply(preco));
            somaPrecos = somaPrecos.add(preco);
            qtdLinhas++;
        }

        if (somaPendente.compareTo(BigDecimal.ZERO) > 0) {
            return somaPonderada.divide(somaPendente, 8, RoundingMode.HALF_UP);
        }
        if (precosIguais && primeiroPreco != null) {
            return primeiroPreco;
        }
        return somaPrecos.divide(BigDecimal.valueOf(qtdLinhas), 8, RoundingMode.HALF_UP);
    }

    private Conferencia persistir(Pedido pedido, String cnpjNota, List<Divergencia> falhas) {
        Conferencia conferencia = new Conferencia();
        conferencia.setPedido(pedido);
        conferencia.setFornecedorCnpj(cnpjNota);
        conferencia.setDataConferencia(Instant.now());
        conferencia.setResultado(falhas.isEmpty() ? ResultadoConferencia.APROVADA : ResultadoConferencia.REJEITADA);
        for (Divergencia falha : falhas) {
            conferencia.adicionarDivergencia(falha);
        }
        return conferenciaRepository.save(conferencia);
    }

    private Divergencia novaDivergencia(TipoDivergencia tipo, String material,
                                       String esperado, String recebido, String descricao) {
        Divergencia divergencia = new Divergencia();
        divergencia.setTipo(tipo);
        divergencia.setCodigoMaterial(material);
        divergencia.setValorEsperado(esperado);
        divergencia.setValorRecebido(recebido);
        divergencia.setDescricao(descricao);
        return divergencia;
    }

    private ConferenciaResponseDTO toResponse(Conferencia conferencia) {
        UUID idPedido = conferencia.getPedido() == null ? null : conferencia.getPedido().getId();
        List<DivergenciaDTO> divergencias = conferencia.getDivergencias().stream()
                .map(d -> new DivergenciaDTO(
                        d.getTipo(),
                        d.getCodigoMaterial(),
                        d.getValorEsperado(),
                        d.getValorRecebido(),
                        d.getDescricao()
                ))
                .toList();
        return new ConferenciaResponseDTO(conferencia.getId(), idPedido, conferencia.getResultado(), divergencias);
    }

    private BigDecimal nvl(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }

    private record AgregadoNota(BigDecimal quantidade, BigDecimal valorTotal) {
        AgregadoNota somar(AgregadoNota outro) {
            return new AgregadoNota(quantidade.add(outro.quantidade), valorTotal.add(outro.valorTotal));
        }
    }
}
