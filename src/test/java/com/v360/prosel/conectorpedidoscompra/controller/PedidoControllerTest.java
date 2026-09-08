package com.v360.prosel.conectorpedidoscompra.controller;

import com.v360.prosel.conectorpedidoscompra.dto.pedido.FornecedorResponseDTO;
import com.v360.prosel.conectorpedidoscompra.dto.pedido.ItemResponseDTO;
import com.v360.prosel.conectorpedidoscompra.dto.pedido.PedidoResumoResponseDTO;
import com.v360.prosel.conectorpedidoscompra.dto.pedido.PedidoResponseDTO;
import com.v360.prosel.conectorpedidoscompra.enums.StatusPedido;
import com.v360.prosel.conectorpedidoscompra.service.PedidoConsultaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({PedidoController.class, ApiExceptionHandler.class})
class PedidoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PedidoConsultaService pedidoConsultaService;

    @Test
    void listar_semResultados_retorna200ListaVazia() throws Exception {
        when(pedidoConsultaService.consultar(isNull(), isNull(), isNull(), isNull())).thenReturn(List.of());

        mockMvc.perform(get("/pedidos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void listar_resumoSemItens_contratoSnakeCase() throws Exception {
        UUID id = UUID.randomUUID();
        PedidoResumoResponseDTO resumo = new PedidoResumoResponseDTO(
                id,
                "PED-1",
                "BETA",
                StatusPedido.OPEN,
                Instant.parse("2026-08-05T03:00:00Z"),
                "BRL",
                new FornecedorResponseDTO(UUID.randomUUID(), "12345678000190", "Fornecedor")
        );
        when(pedidoConsultaService.consultar(eq("BETA"), eq("12345678000190"), eq(StatusPedido.OPEN), eq(true)))
                .thenReturn(List.of(resumo));

        mockMvc.perform(get("/pedidos")
                        .param("cliente_origem", "BETA")
                        .param("fornecedor", "12345678000190")
                        .param("status", "OPEN")
                        .param("com_pendencia", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id_pedido").value(id.toString()))
                .andExpect(jsonPath("$[0].numero_pedido").value("PED-1"))
                .andExpect(jsonPath("$[0].itens").doesNotExist())
                .andExpect(jsonPath("$[0].fornecedor.cnpj").value("12345678000190"));
    }

    @Test
    void listar_statusInvalido_retorna400() throws Exception {
        mockMvc.perform(get("/pedidos").param("status", "FOO"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.mensagem").value("status inválido"));
    }

    @Test
    void detalhar_inexistente_retorna404() throws Exception {
        UUID id = UUID.randomUUID();
        when(pedidoConsultaService.consultarPorId(id))
                .thenThrow(new ResponseStatusException(NOT_FOUND, "pedido não encontrado"));

        mockMvc.perform(get("/pedidos/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.mensagem").value("pedido não encontrado"));
    }

    @Test
    void detalhar_comItens_incluiQuantidadePendente() throws Exception {
        UUID id = UUID.randomUUID();
        PedidoResponseDTO detalhe = new PedidoResponseDTO(
                id,
                "PED-1",
                "BETA",
                StatusPedido.OPEN,
                Instant.parse("2026-08-05T03:00:00Z"),
                "BRL",
                new FornecedorResponseDTO(UUID.randomUUID(), "12345678000190", "Fornecedor"),
                List.of(new ItemResponseDTO(
                        UUID.randomUUID(), "1", "MAT-1", "Item", "UN",
                        new BigDecimal("7"), new BigDecimal("2"), new BigDecimal("5"),
                        new BigDecimal("10.00")
                ))
        );
        when(pedidoConsultaService.consultarPorId(id)).thenReturn(detalhe);

        mockMvc.perform(get("/pedidos/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id_pedido").value(id.toString()))
                .andExpect(jsonPath("$.itens[0].quantidade_pedida").value(7))
                .andExpect(jsonPath("$.itens[0].quantidade_recebida").value(2))
                .andExpect(jsonPath("$.itens[0].quantidade_pendente").value(5));
    }
}
