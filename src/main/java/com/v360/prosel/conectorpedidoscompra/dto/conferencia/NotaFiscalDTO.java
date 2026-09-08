package com.v360.prosel.conectorpedidoscompra.dto.conferencia;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class NotaFiscalDTO {
    @NotBlank
    @JsonProperty("numero_pedido")
    private String numeroPedido;

    @NotBlank
    @JsonProperty("cliente_origem")
    private String clienteOrigem;

    @NotBlank
    @JsonProperty("fornecedor_cnpj")
    private String fornecedorCnpj;

    @NotEmpty
    @JsonProperty("itens")
    private List<@Valid NotaFiscalItemDTO> itens = new ArrayList<>();

    public NotaFiscalDTO(String numeroPedido, String clienteOrigem, String fornecedorCnpj,
                         List<NotaFiscalItemDTO> itens) {
        this.numeroPedido = numeroPedido;
        this.clienteOrigem = clienteOrigem;
        this.fornecedorCnpj = fornecedorCnpj;
        this.itens = itens;
    }
}
