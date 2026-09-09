package com.v360.prosel.conectorpedidoscompra.parser;

import com.v360.prosel.conectorpedidoscompra.dto.gama.GamaItemLinhaDTO;
import com.v360.prosel.conectorpedidoscompra.dto.gama.GamaPedidoDTO;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Agrupa o JSON achatado do Gama por {@code ped} e valida que os campos de
 * cabeçalho repetidos em cada linha são consistentes. Espelha o papel do
 * join do {@code BetaCsvParser} — o ingestor recebe um pedido já aninhado.
 */
public final class GamaPayloadAgrupador {

    private GamaPayloadAgrupador() {
    }

    public static List<GamaPedidoDTO> agrupar(List<GamaItemLinhaDTO> linhas) {
        if (linhas == null || linhas.isEmpty()) {
            throw new IllegalArgumentException("Payload Gama não pode ser vazio");
        }

        Map<String, List<GamaItemLinhaDTO>> porPed = new LinkedHashMap<>();
        for (GamaItemLinhaDTO linha : linhas) {
            porPed.computeIfAbsent(linha.ped(), k -> new ArrayList<>()).add(linha);
        }

        List<GamaPedidoDTO> pedidos = new ArrayList<>();
        for (List<GamaItemLinhaDTO> grupo : porPed.values()) {
            validarConsistencia(grupo);
            GamaItemLinhaDTO primeira = grupo.get(0);
            pedidos.add(new GamaPedidoDTO(
                    primeira.ped(),
                    primeira.cnpjFornecedor(),
                    primeira.nomeFornecedor(),
                    primeira.dtCriacao(),
                    primeira.situacao(),
                    List.copyOf(grupo)
            ));
        }
        return pedidos;
    }

    private static void validarConsistencia(List<GamaItemLinhaDTO> linhasDoPedido) {
        GamaItemLinhaDTO primeira = linhasDoPedido.get(0);
        for (GamaItemLinhaDTO linha : linhasDoPedido) {
            if (!Objects.equals(linha.cnpjFornecedor(), primeira.cnpjFornecedor())
                    || !Objects.equals(linha.nomeFornecedor(), primeira.nomeFornecedor())
                    || !Objects.equals(linha.dtCriacao(), primeira.dtCriacao())
                    || !Objects.equals(linha.situacao(), primeira.situacao())) {
                throw new IllegalArgumentException(
                        "Dados de cabeçalho inconsistentes para o pedido " + linha.ped());
            }
        }
    }
}
