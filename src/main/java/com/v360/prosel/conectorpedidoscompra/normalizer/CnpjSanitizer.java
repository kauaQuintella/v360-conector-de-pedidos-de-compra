package com.v360.prosel.conectorpedidoscompra.normalizer;

/**
 * Utilitário puro (sem estado, sem Spring) para sanitização de CNPJ.
 * Centraliza a lógica de remoção de máscara para que todos os Ingestors
 * produzam sempre um CNPJ com apenas dígitos (DESING.md seção 2).
 */
public class CnpjSanitizer {

    private CnpjSanitizer() {
        // utilitário estático — instanciação desnecessária
    }

    /**
     * Remove qualquer caractere não-numérico do CNPJ.
     * Ex.: "12.345.678/0001-90" → "12345678000190"
     *
     * @param cnpj CNPJ bruto, possivelmente com máscara
     * @return CNPJ só com dígitos, ou null se a entrada for null
     */
    public static String sanitize(String cnpj) {
        if (cnpj == null) return null;
        return cnpj.replaceAll("[^0-9]", "");
    }
}
