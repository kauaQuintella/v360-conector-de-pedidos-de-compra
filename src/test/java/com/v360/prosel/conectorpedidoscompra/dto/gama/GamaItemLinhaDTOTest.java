package com.v360.prosel.conectorpedidoscompra.dto.gama;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GamaItemLinhaDTOTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("Payload oficial desserializa nas 3 linhas e nos 13 campos da primeira")
    void desserializa_payloadOficial_mapeiaTrezeCamposDaPrimeiraLinha() throws Exception {
        List<GamaItemLinhaDTO> linhas = mapper.readValue(
                Path.of("public/gamaLogisticaPayload.json").toFile(),
                new TypeReference<>() {}
        );

        assertThat(linhas).hasSize(3);

        GamaItemLinhaDTO primeira = linhas.get(0);
        assertThat(primeira.ped()).isEqualTo("GL-778");
        assertThat(primeira.item()).isEqualTo(1);
        assertThat(primeira.cnpjFornecedor()).isEqualTo("34567890000112");
        assertThat(primeira.nomeFornecedor()).isEqualTo("Transportes Ideal ME");
        assertThat(primeira.dtCriacao()).isEqualTo(1786752000L);
        assertThat(primeira.codMat()).isEqualTo("TRP-01");
        assertThat(primeira.descMat()).isEqualTo("Pallet de madeira");
        assertThat(primeira.um()).isEqualTo("CX");
        assertThat(primeira.fatorConv()).isEqualTo(12);
        assertThat(primeira.qtdPed()).isEqualByComparingTo(new BigDecimal("10"));
        assertThat(primeira.qtdRec()).isEqualByComparingTo(new BigDecimal("2"));
        assertThat(primeira.precoUnitCentavos()).isEqualTo(120000L);
        assertThat(primeira.situacao()).isEqualTo(1);
    }
}
