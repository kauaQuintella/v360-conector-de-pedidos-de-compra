package com.v360.prosel.conectorpedidoscompra.normalizer;

/**
 * Utilitário puro (sem estado, sem Spring) para sanitização de strings textuais.
 *
 * Centraliza a remoção de caracteres de controle invisíveis (\n, \r, \t) de
 * campos cadastrais de linha única, como nomes de fornecedores e descrições.
 * Evita quebras em relatórios CSV, inconsistências em buscas e falhas em
 * integrações downstream.
 */
public class StringSanitizer {

    private StringSanitizer() {
        // utilitário estático — instanciação desnecessária
    }

    /**
     * Remove quebras de linha (\n, \r) e tabulações (\t) de uma string,
     * substituindo-as por espaço simples, e aplica trim() no resultado.
     *
     * Ex.: "Metalúrgica São Jorge\nS.A." → "Metalúrgica São Jorge S.A."
     *
     * @param valor string bruta recebida do cliente
     * @return string sanitizada, ou null se a entrada for null
     */
    public static String sanitize(String valor) {
        if (valor == null) return null;
        return valor.replaceAll("[\\n\\r\\t]", " ").trim();
    }
}