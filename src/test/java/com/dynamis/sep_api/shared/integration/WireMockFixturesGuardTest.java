package com.dynamis.sep_api.shared.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guard das fixtures WireMock (Sprint 32 Task 32.6): nenhum host externo, dominio real de
 * provider ou segredo de ambiente pode entrar em {@code src/test/resources/wiremock/}. As
 * fixtures sao skeletons locais com dados claramente ficticios.
 */
class WireMockFixturesGuardTest {

    private static final Path RAIZ = Path.of("src/test/resources/wiremock");

    private static final List<String> PROIBIDOS = List.of(
            "https://", "sandbox.", "celcoin.dev", "celcoin.com", "clicksign.com", "change-me", "amazonaws.com");

    @Test
    void fixtures_saoJsonValidoSemHostOuSegredoReal() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        try (Stream<Path> arquivos = Files.walk(RAIZ)) {
            List<Path> jsons =
                    arquivos.filter(p -> p.toString().endsWith(".json")).toList();
            assertThat(jsons).as("fixtures existem").isNotEmpty();
            for (Path json : jsons) {
                String conteudo = Files.readString(json);
                for (String proibido : PROIBIDOS) {
                    assertThat(conteudo)
                            .as(json + " nao pode conter '" + proibido + "'")
                            .doesNotContain(proibido);
                }
                // http:// so e aceito se apontar para localhost.
                assertThat(conteudo.contains("http://") && !conteudo.contains("http://localhost"))
                        .as(json + " nao pode apontar para host http externo")
                        .isFalse();
                mapper.readTree(conteudo); // JSON invalido falha aqui.
            }
        }
    }

    @Test
    void fixtures_cobremAsQuatroCapacidades() {
        for (String capacidade : List.of("onboarding", "assinatura", "pix", "escrow")) {
            assertThat(RAIZ.resolve(capacidade).resolve("mappings"))
                    .as("mappings de " + capacidade)
                    .isDirectory();
        }
    }
}
