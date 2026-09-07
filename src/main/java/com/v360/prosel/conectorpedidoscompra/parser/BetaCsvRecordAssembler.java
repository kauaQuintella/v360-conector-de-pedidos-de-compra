package com.v360.prosel.conectorpedidoscompra.parser;

import java.util.ArrayList;
import java.util.List;

/**
 * Remonta registros lógicos de CSV sem aspas cujo {@code \n} pode cair no meio do campo.
 *
 * Máquina de estados por contagem de {@code ;}, com lookahead quando o cursor
 * já está no último campo (N-1 separadores). Não inspeciona conteúdo
 * (número de pedido, nome de coluna).
 */
public final class BetaCsvRecordAssembler {

    private BetaCsvRecordAssembler() {
    }

    /**
     * @param text        CSV bruto (UTF-8 já decodificado)
     * @param columns     número fixo de colunas do arquivo (6 cabeçalho, 8 itens)
     * @param skipHeader  se true, descarta o primeiro registro lógico
     */
    public static List<String> assemble(String text, int columns, boolean skipHeader) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        if (columns < 2) {
            throw new IllegalArgumentException("Número de colunas deve ser >= 2");
        }

        int nMinus1 = columns - 1;
        List<String> records = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int seps = 0;
        int i = 0;
        int len = text.length();

        while (i < len) {
            char c = text.charAt(i);

            if (c == ';') {
                seps++;
                current.append(c);
                i++;
                continue;
            }

            if (c == '\n' || c == '\r') {
                int afterNewline = skipNewline(text, i);

                if (current.isEmpty() && seps == 0) {
                    i = afterNewline;
                    continue;
                }

                if (seps < nMinus1) {
                    current.append(' ');
                    i = afterNewline;
                    continue;
                }

                // seps >= nMinus1: último campo — lookahead da próxima linha física
                if (afterNewline >= len) {
                    closeRecord(records, current);
                    seps = 0;
                    i = afterNewline;
                    continue;
                }

                int nextLineEnd = physicalLineEnd(text, afterNewline);
                String nextPhysical = text.substring(afterNewline, nextLineEnd);
                int nextSeps = countSeparators(nextPhysical);

                if (nextSeps == 0) {
                    current.append(' ');
                    current.append(nextPhysical);
                    i = nextLineEnd;
                    continue;
                }

                closeRecord(records, current);
                seps = 0;
                i = afterNewline;
                continue;
            }

            current.append(c);
            i++;
        }

        if (!current.isEmpty()) {
            records.add(current.toString());
        }

        if (skipHeader && !records.isEmpty()) {
            return List.copyOf(records.subList(1, records.size()));
        }
        return List.copyOf(records);
    }

    private static void closeRecord(List<String> records, StringBuilder current) {
        records.add(current.toString());
        current.setLength(0);
    }

    private static int skipNewline(String text, int i) {
        if (text.charAt(i) == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
            return i + 2;
        }
        return i + 1;
    }

    private static int physicalLineEnd(String text, int from) {
        for (int i = from; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\r') {
                return i;
            }
        }
        return text.length();
    }

    private static int countSeparators(String line) {
        int n = 0;
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == ';') {
                n++;
            }
        }
        return n;
    }
}
