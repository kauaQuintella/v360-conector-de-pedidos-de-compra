package com.v360.prosel.conectorpedidoscompra.parser;

import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.exceptions.CsvException;
import com.v360.prosel.conectorpedidoscompra.dto.beta.BetaCabecalhoLinhaDTO;
import com.v360.prosel.conectorpedidoscompra.dto.beta.BetaItemDTO;
import com.v360.prosel.conectorpedidoscompra.dto.beta.BetaItemLinhaDTO;
import com.v360.prosel.conectorpedidoscompra.dto.beta.BetaPedidoDTO;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lê os dois CSVs do Beta: remontagem → OpenCSV por posição → join por NUMERO_PEDIDO.
 */
@Component
public class BetaCsvParser {

    public static final int COLUNAS_CABECALHO = 6;
    public static final int COLUNAS_ITENS = 8;

    public List<BetaPedidoDTO> parse(InputStream cabecalho, InputStream itens) {
        return parse(
                new String(readAll(cabecalho), StandardCharsets.UTF_8),
                new String(readAll(itens), StandardCharsets.UTF_8)
        );
    }

    public List<BetaPedidoDTO> parse(String cabecalhoCsv, String itensCsv) {
        List<String> linhasCabecalho = BetaCsvRecordAssembler.assemble(
                cabecalhoCsv, COLUNAS_CABECALHO, true);
        List<String> linhasItens = BetaCsvRecordAssembler.assemble(
                itensCsv, COLUNAS_ITENS, true);

        List<BetaCabecalhoLinhaDTO> cabecalhos = toBeans(linhasCabecalho, BetaCabecalhoLinhaDTO.class);
        List<BetaItemLinhaDTO> itens = toBeans(linhasItens, BetaItemLinhaDTO.class);
        return join(cabecalhos, itens);
    }

    List<BetaPedidoDTO> join(List<BetaCabecalhoLinhaDTO> cabecalhos, List<BetaItemLinhaDTO> itens) {
        Map<String, BetaCabecalhoLinhaDTO> porNumero = new LinkedHashMap<>();
        for (BetaCabecalhoLinhaDTO cabecalho : cabecalhos) {
            String numero = requireNumero(cabecalho.getNumeroPedido(), "cabeçalho");
            porNumero.put(numero, cabecalho);
        }

        Map<String, List<BetaItemLinhaDTO>> itensPorNumero = new LinkedHashMap<>();
        for (BetaItemLinhaDTO item : itens) {
            String numero = requireNumero(item.getNumeroPedido(), "item");
            if (!porNumero.containsKey(numero)) {
                throw new IllegalArgumentException(
                        "Item sem cabeçalho correspondente: NUMERO_PEDIDO=" + numero);
            }
            itensPorNumero.computeIfAbsent(numero, k -> new ArrayList<>()).add(item);
        }

        List<BetaPedidoDTO> pedidos = new ArrayList<>();
        for (Map.Entry<String, BetaCabecalhoLinhaDTO> entry : porNumero.entrySet()) {
            String numero = entry.getKey();
            List<BetaItemLinhaDTO> itensDoPedido = itensPorNumero.get(numero);
            if (itensDoPedido == null || itensDoPedido.isEmpty()) {
                throw new IllegalArgumentException(
                        "Cabeçalho sem itens: NUMERO_PEDIDO=" + numero);
            }
            BetaCabecalhoLinhaDTO c = entry.getValue();
            pedidos.add(new BetaPedidoDTO(
                    numero,
                    c.getFornecedorCnpj(),
                    c.getFornecedorRazaoSocial(),
                    c.getEmissao(),
                    c.getSituacao(),
                    c.getMoeda(),
                    itensDoPedido.stream().map(this::toItemDto).toList()
            ));
        }
        return pedidos;
    }

    private BetaItemDTO toItemDto(BetaItemLinhaDTO linha) {
        return new BetaItemDTO(
                linha.getItem(),
                linha.getCodigoMaterial(),
                linha.getDescricao(),
                linha.getUnidade(),
                linha.getQtdPedida(),
                linha.getQtdRecebida(),
                linha.getPrecoUnitario()
        );
    }

    private static String requireNumero(String numero, String origem) {
        if (numero == null || numero.isBlank()) {
            throw new IllegalArgumentException("NUMERO_PEDIDO ausente na linha de " + origem);
        }
        return numero.trim();
    }

    private static <T> List<T> toBeans(List<String> linhasLogicas, Class<T> type) {
        if (linhasLogicas.isEmpty()) {
            return List.of();
        }
        String csv = String.join("\n", linhasLogicas);
        try (CSVReader reader = new CSVReaderBuilder(new StringReader(csv))
                .withCSVParser(new CSVParserBuilder().withSeparator(';').build())
                .build()) {
            return new CsvToBeanBuilder<T>(reader)
                    .withType(type)
                    .withSeparator(';')
                    .withIgnoreLeadingWhiteSpace(true)
                    .withThrowExceptions(true)
                    .build()
                    .parse();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (RuntimeException e) {
            if (e.getCause() instanceof CsvException csvException) {
                throw new IllegalArgumentException("Falha ao mapear CSV Beta: " + csvException.getMessage(), e);
            }
            throw e;
        }
    }

    private static byte[] readAll(InputStream in) {
        try {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
