package com.v360.prosel.conectorpedidoscompra.dto.beta;

import com.opencsv.bean.CsvBindByPosition;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Linha lógica do itens.csv após remontagem. Ordem fixa da spec Beta.
 */
@Getter
@Setter
@NoArgsConstructor
public class BetaItemLinhaDTO {

    @CsvBindByPosition(position = 0)
    private String numeroPedido;

    @CsvBindByPosition(position = 1)
    private String item;

    @CsvBindByPosition(position = 2)
    private String codigoMaterial;

    @CsvBindByPosition(position = 3)
    private String descricao;

    @CsvBindByPosition(position = 4)
    private String unidade;

    @CsvBindByPosition(position = 5)
    private String qtdPedida;

    @CsvBindByPosition(position = 6)
    private String qtdRecebida;

    @CsvBindByPosition(position = 7)
    private String precoUnitario;
}
