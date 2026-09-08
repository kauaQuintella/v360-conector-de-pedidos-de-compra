package com.v360.prosel.conectorpedidoscompra.controller;

import com.v360.prosel.conectorpedidoscompra.dto.conferencia.ConferenciaResponseDTO;
import com.v360.prosel.conectorpedidoscompra.dto.conferencia.DivergenciaDTO;
import com.v360.prosel.conectorpedidoscompra.dto.conferencia.RelatorioConferenciasDTO;
import com.v360.prosel.conectorpedidoscompra.enums.ResultadoConferencia;
import com.v360.prosel.conectorpedidoscompra.enums.TipoDivergencia;
import com.v360.prosel.conectorpedidoscompra.service.ConferenciaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({ConferenciaController.class, ApiExceptionHandler.class})
class ConferenciaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConferenciaService conferenciaService;

    @Test
    void conferir_pedidoInexistente_retorna200() throws Exception {
        UUID id = UUID.randomUUID();
        when(conferenciaService.conferir(any())).thenReturn(new ConferenciaResponseDTO(
                id,
                null,
                ResultadoConferencia.REJEITADA,
                List.of(new DivergenciaDTO(
                        TipoDivergencia.PEDIDO_NAO_ENCONTRADO, null, "PED-X/BETA", "PED-X",
                        "Pedido não encontrado pela chave (numero_pedido, cliente_origem)."
                ))
        ));

        mockMvc.perform(post("/notas-fiscais/conferir")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "numero_pedido": "PED-X",
                                  "cliente_origem": "BETA",
                                  "fornecedor_cnpj": "12345678000190",
                                  "itens": [
                                    { "codigo_material": "MAT-1", "quantidade": 1, "valor_total": 10.00 }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id_conferencia").value(id.toString()))
                .andExpect(jsonPath("$.resultado").value("REJEITADA"))
                .andExpect(jsonPath("$.divergencias[0].tipo").value("PEDIDO_NAO_ENCONTRADO"));
    }

    @Test
    void conferir_payloadInvalido_retorna400() throws Exception {
        mockMvc.perform(post("/notas-fiscais/conferir")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "numero_pedido": "", "cliente_origem": "BETA", "fornecedor_cnpj": "1", "itens": [] }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void relatorio_retornaContagens() throws Exception {
        when(conferenciaService.relatorio()).thenReturn(new RelatorioConferenciasDTO(
                5,
                Map.of("APROVADA", 2L, "REJEITADA", 3L),
                Map.of("VALOR_DIVERGENTE", 1L)
        ));

        mockMvc.perform(get("/relatorios/conferencias"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.por_resultado.REJEITADA").value(3))
                .andExpect(jsonPath("$.por_tipo.VALOR_DIVERGENTE").value(1));
    }
}
