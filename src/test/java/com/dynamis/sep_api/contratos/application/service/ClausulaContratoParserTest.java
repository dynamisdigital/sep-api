package com.dynamis.sep_api.contratos.application.service;

import com.dynamis.sep_api.contratos.application.port.out.dto.ClausulaRenderizada;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClausulaContratoParserTest {

    private final ClausulaContratoParser parser = new ClausulaContratoParser();

    @Test
    void parse_extraiClausulasEmOrdem() {
        String texto =
                """
                Preambulo deve ser ignorado.

                CLAUSULA 1 - OBJETO
                Texto da clausula 1.

                CLAUSULA 2 - PRAZO
                Texto da clausula 2.
                Linha extra.

                CLAUSULA 3 - FORO
                Texto da clausula 3.
                """;

        List<ClausulaRenderizada> r = parser.parse(texto);

        assertThat(r).hasSize(3);
        assertThat(r).extracting(ClausulaRenderizada::ordem).containsExactly(1, 2, 3);
        assertThat(r).extracting(ClausulaRenderizada::titulo).containsExactly("OBJETO", "PRAZO", "FORO");
        assertThat(r.get(1).texto()).contains("Texto da clausula 2.").contains("Linha extra.");
    }

    @Test
    void parse_textoSemMarcadorRetornaListaVazia() {
        assertThat(parser.parse("Apenas preambulo, sem clausulas.")).isEmpty();
    }

    @Test
    void parse_nulloOuVazioRetornaListaVazia() {
        assertThat(parser.parse(null)).isEmpty();
        assertThat(parser.parse("  \n  ")).isEmpty();
    }
}
