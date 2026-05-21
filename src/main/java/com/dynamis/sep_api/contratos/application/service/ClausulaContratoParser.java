package com.dynamis.sep_api.contratos.application.service;

import com.dynamis.sep_api.contratos.application.port.out.dto.ClausulaRenderizada;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrai clausulas de um texto renderizado pelo template engine. Marcador esperado:
 *
 * <pre>
 *   CLAUSULA &lt;N&gt; - &lt;TITULO&gt;
 *   ...texto da clausula...
 *   CLAUSULA &lt;N+1&gt; - &lt;TITULO&gt;
 *   ...
 * </pre>
 *
 * <p>Linhas antes da primeira clausula sao consideradas preambulo e ignoradas pelo parser. O
 * texto de cada clausula vai da linha do marcador (exclusive) ate o proximo marcador (exclusive).
 */
@Component
public class ClausulaContratoParser {

    private static final Pattern MARCADOR =
            Pattern.compile("^\\s*CLAUSULA\\s+(\\d+)\\s*-\\s*(.+?)\\s*$", Pattern.MULTILINE);

    public List<ClausulaRenderizada> parse(String texto) {
        if (texto == null || texto.isBlank()) {
            return List.of();
        }
        Matcher m = MARCADOR.matcher(texto);
        List<int[]> marcadores = new ArrayList<>();
        List<String> titulos = new ArrayList<>();
        List<Integer> ordens = new ArrayList<>();
        while (m.find()) {
            marcadores.add(new int[] {m.start(), m.end()});
            ordens.add(Integer.parseInt(m.group(1)));
            titulos.add(m.group(2).trim());
        }
        if (marcadores.isEmpty()) {
            return List.of();
        }
        List<ClausulaRenderizada> resultado = new ArrayList<>(marcadores.size());
        for (int i = 0; i < marcadores.size(); i++) {
            int inicioTexto = marcadores.get(i)[1];
            int fimTexto = i + 1 < marcadores.size() ? marcadores.get(i + 1)[0] : texto.length();
            String conteudo = texto.substring(inicioTexto, fimTexto).trim();
            resultado.add(new ClausulaRenderizada(ordens.get(i), titulos.get(i), conteudo));
        }
        return List.copyOf(resultado);
    }
}
