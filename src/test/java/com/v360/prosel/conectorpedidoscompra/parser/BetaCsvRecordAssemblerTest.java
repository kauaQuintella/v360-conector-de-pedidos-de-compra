package com.v360.prosel.conectorpedidoscompra.parser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testes unitários de {@link BetaCsvRecordAssembler}.
 *
 * Cobre: payload oficial, quebra em campo do meio diferente de SITUACAO,
 * arquivo sem quebra de linha, header partido no último campo (lookahead),
 * entrada vazia e parâmetros inválidos.
 */
class BetaCsvRecordAssemblerTest {

    // =========================================================================
    // Payload oficial — cabecalho.csv (6 colunas)
    // =========================================================================

    @Test
    @DisplayName("Payload oficial cabecalho.csv: dois registros, quebras no meio do campo")
    void assemble_cabecalhoOficial_doisRegistros() {
        // Reproduz exatamente o arquivo public/betaAlimentos/cabecalho.csv
        String csv = "NUMERO_PEDIDO;FORNECEDOR_CNPJ;FORNECEDOR_RAZAO_SOCIAL;EMISSAO;SITUACAO;MOEDA\n"
                + "20260088412;12.345.678/0001-90;Distribuidora Horizonte Ltda;15/08/2026;EM\n"
                + "ABERTO;BRL\n"
                + "20260088413;98.765.432/0001-55;Frigorífico Boa Mesa\n"
                + "S.A.;01/08/2026;BLOQUEADO;BRL\n";

        List<String> records = BetaCsvRecordAssembler.assemble(csv, 6, true);

        assertThat(records).hasSize(2);
        assertThat(records.get(0))
                .isEqualTo("20260088412;12.345.678/0001-90;Distribuidora Horizonte Ltda;15/08/2026;EM ABERTO;BRL");
        assertThat(records.get(1))
                .isEqualTo("20260088413;98.765.432/0001-55;Frigorífico Boa Mesa S.A.;01/08/2026;BLOQUEADO;BRL");
    }

    // =========================================================================
    // Payload oficial — itens.csv (8 colunas, header partido no último campo)
    // =========================================================================

    @Test
    @DisplayName("Payload oficial itens.csv: header partido no último campo não vaza para dados")
    void assemble_itensOficial_tresRegistrosDadosCorretos() {
        // Reproduz exatamente o arquivo public/betaAlimentos/itens.csv
        String csv = "NUMERO_PEDIDO;ITEM;CODIGO_MATERIAL;DESCRICAO;UNIDADE;QTD_PEDIDA;QTD_RECEBIDA;PR\n"
                + "ECO_UNITARIO\n"
                + "20260088412;1;MAT-77;Óleo de soja 900ml;UN;1.200,000;400,000;6,49\n"
                + "20260088412;2;MAT-78;Açúcar refinado 1kg;UN;500,000;0,000;4,15\n"
                + "20260088413;1;MAT-91;Carne bovina dianteiro kg;KG;2.000,000;0,000;27,90\n";

        List<String> records = BetaCsvRecordAssembler.assemble(csv, 8, true);

        assertThat(records).hasSize(3);
        assertThat(records.get(0))
                .isEqualTo("20260088412;1;MAT-77;Óleo de soja 900ml;UN;1.200,000;400,000;6,49");
        assertThat(records.get(1))
                .isEqualTo("20260088412;2;MAT-78;Açúcar refinado 1kg;UN;500,000;0,000;4,15");
        assertThat(records.get(2))
                .isEqualTo("20260088413;1;MAT-91;Carne bovina dianteiro kg;KG;2.000,000;0,000;27,90");
    }

    @Test
    @DisplayName("Header partido no último campo: ECO_UNITARIO não se cola ao primeiro dado")
    void assemble_headerPartidoNoUltimoCampo_headerDescartadoSemContaminacao() {
        String csv = "COL1;COL2;COL3;PR\n"
                + "ECO_UNITARIO\n"
                + "A;B;C;6,49\n"
                + "D;E;F;4,15\n";

        List<String> records = BetaCsvRecordAssembler.assemble(csv, 4, true);

        assertThat(records).hasSize(2);
        // O primeiro campo dos dados deve ser "A", não "ECO_UNITARIO A"
        assertThat(records.get(0)).isEqualTo("A;B;C;6,49");
        assertThat(records.get(1)).isEqualTo("D;E;F;4,15");
    }

    // =========================================================================
    // Quebra de linha em campo que não é SITUACAO (máquina não depende de conteúdo)
    // =========================================================================

    @Test
    @DisplayName("Quebra em campo 1 (não SITUACAO): máquina de estados não depende de conteúdo")
    void assemble_quebraEmCampoMeio_diferenteDeUltimo_concatenaComEspaco() {
        // Quebra no campo 2 (RAZAO_SOCIAL), 6 colunas
        String csv = "H1;H2;H3;H4;H5;H6\n"
                + "P001;CNPJ;Nome\n"
                + "Longa Empresa Ltda;01/01/2026;ABERTO;BRL\n";

        List<String> records = BetaCsvRecordAssembler.assemble(csv, 6, true);

        assertThat(records).hasSize(1);
        assertThat(records.get(0))
                .isEqualTo("P001;CNPJ;Nome Longa Empresa Ltda;01/01/2026;ABERTO;BRL");
    }

    @Test
    @DisplayName("Múltiplas quebras de linha no mesmo campo do meio")
    void assemble_multiplosNewlineNoMesmoCampo_concatenaTudoComEspaco() {
        String csv = "H1;H2;H3\n"
                + "A;campo\nem\nvarias\nlinhas;C\n";

        List<String> records = BetaCsvRecordAssembler.assemble(csv, 3, true);

        assertThat(records).hasSize(1);
        assertThat(records.get(0)).isEqualTo("A;campo em varias linhas;C");
    }

    // =========================================================================
    // Arquivo sem quebra de linha — não "corrigir" o que já está certo
    // =========================================================================

    @Test
    @DisplayName("Arquivo sem quebra de linha interna: registros passam intactos")
    void assemble_semQuebraInternaNosCampos_registrosIntactos() {
        String csv = "H1;H2;H3\n"
                + "A1;B1;C1\n"
                + "A2;B2;C2\n";

        List<String> records = BetaCsvRecordAssembler.assemble(csv, 3, true);

        assertThat(records).hasSize(2);
        assertThat(records.get(0)).isEqualTo("A1;B1;C1");
        assertThat(records.get(1)).isEqualTo("A2;B2;C2");
    }

    @Test
    @DisplayName("Arquivo sem quebra de linha: skipHeader=false mantém o header")
    void assemble_skipHeaderFalse_incluiHeader() {
        String csv = "H1;H2\nA;B\n";

        List<String> records = BetaCsvRecordAssembler.assemble(csv, 2, false);

        assertThat(records).hasSize(2);
        assertThat(records.get(0)).isEqualTo("H1;H2");
        assertThat(records.get(1)).isEqualTo("A;B");
    }

    // =========================================================================
    // CRLF (Windows)
    // =========================================================================

    @Test
    @DisplayName("Arquivo com CRLF: tratado como LF simples")
    void assemble_crlf_tratadoCorretamente() {
        String csv = "H1;H2;H3\r\nA;B;C\r\nD;E;F\r\n";

        List<String> records = BetaCsvRecordAssembler.assemble(csv, 3, true);

        assertThat(records).hasSize(2);
        assertThat(records.get(0)).isEqualTo("A;B;C");
        assertThat(records.get(1)).isEqualTo("D;E;F");
    }

    // =========================================================================
    // Entradas inválidas / edge cases
    // =========================================================================

    @Test
    @DisplayName("Entrada nula retorna lista vazia")
    void assemble_entradaNula_retornaListaVazia() {
        List<String> records = BetaCsvRecordAssembler.assemble(null, 6, true);
        assertThat(records).isEmpty();
    }

    @Test
    @DisplayName("Entrada vazia retorna lista vazia")
    void assemble_entradaVazia_retornaListaVazia() {
        List<String> records = BetaCsvRecordAssembler.assemble("", 6, true);
        assertThat(records).isEmpty();
    }

    @Test
    @DisplayName("columns < 2 lança IllegalArgumentException")
    void assemble_colunasMenorQueDois_lancaExcecao() {
        assertThatThrownBy(() -> BetaCsvRecordAssembler.assemble("A;B\n", 1, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Arquivo com apenas header e skipHeader=true retorna lista vazia")
    void assemble_soHeader_retornaListaVazia() {
        String csv = "H1;H2;H3\n";
        List<String> records = BetaCsvRecordAssembler.assemble(csv, 3, true);
        assertThat(records).isEmpty();
    }

    @Test
    @DisplayName("Último registro sem newline final é emitido corretamente")
    void assemble_semNewlineFinal_ultimoRegistroEmitido() {
        String csv = "H1;H2\nA;B\nC;D"; // sem \n no final

        List<String> records = BetaCsvRecordAssembler.assemble(csv, 2, true);

        assertThat(records).hasSize(2);
        assertThat(records.get(0)).isEqualTo("A;B");
        assertThat(records.get(1)).isEqualTo("C;D");
    }
}
