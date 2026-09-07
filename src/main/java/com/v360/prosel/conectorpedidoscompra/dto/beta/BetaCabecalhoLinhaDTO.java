package com.v360.prosel.conectorpedidoscompra.dto.beta;

import com.opencsv.bean.CsvBindByPosition;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Linha lógica do cabecalho.csv após remontagem. Ordem fixa da spec Beta.
 */
@Getter
@Setter
@NoArgsConstructor
public class BetaCabecalhoLinhaDTO {

    @CsvBindByPosition(position = 0)
    private String numeroPedido;

    @CsvBindByPosition(position = 1)
    private String fornecedorCnpj;

    @CsvBindByPosition(position = 2)
    private String fornecedorRazaoSocial;

    @CsvBindByPosition(position = 3)
    private String emissao;

    @CsvBindByPosition(position = 4)
    private String situacao;

    @CsvBindByPosition(position = 5)
    private String moeda;
}
