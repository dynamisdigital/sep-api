package com.dynamis.sep_api.backoffice.application.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardBackofficeTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void serializaVazio_naoLanca() throws Exception {
        DashboardBackoffice d = new DashboardBackoffice(
                List.of(),
                List.of(),
                List.of(),
                Duration.ZERO,
                0L,
                List.of(),
                BigDecimal.ZERO,
                InadimplenciaConsolidada.vazia(),
                List.of(),
                Instant.parse("2026-05-26T12:00:00Z"));

        String json = mapper.writeValueAsString(d);

        assertThat(json)
                .contains("\"contadoresPorTipo\":[]")
                .contains("\"itensCriticosAbertosMais48h\":0")
                .contains("\"recebimentosDoDia\":0")
                .contains("\"geradoEm\"");
    }

    @Test
    void serializaCompleto_contemTodosOsCampos() throws Exception {
        DashboardBackoffice d = new DashboardBackoffice(
                List.of(),
                List.of(),
                List.of(),
                Duration.ofSeconds(7200),
                3L,
                List.of(),
                new BigDecimal("12345.67"),
                new InadimplenciaConsolidada(new BigDecimal("9000.00"), 3),
                List.of(new ContadorPorStatusProposta("EM_ANALISE", 4)),
                Instant.parse("2026-05-26T15:00:00Z"));

        String json = mapper.writeValueAsString(d);

        assertThat(json)
                .contains("\"tempoMedioResolucao30d\"")
                .contains("\"itensCriticosAbertosMais48h\":3")
                .contains("\"recebimentosDoDia\":12345.67")
                .contains("\"inadimplenciaTotal\":{")
                .contains("\"valorTotal\":9000.00")
                .contains("\"numeroParcelas\":3")
                .contains("\"EM_ANALISE\"");
    }

    @Test
    void roundtrip_preservaCampos() throws Exception {
        DashboardBackoffice original = new DashboardBackoffice(
                List.of(),
                List.of(),
                List.of(),
                Duration.ofSeconds(100),
                1L,
                List.of(),
                new BigDecimal("250.00"),
                new InadimplenciaConsolidada(new BigDecimal("500.00"), 2),
                List.of(),
                Instant.parse("2026-05-26T10:00:00Z"));

        String json = mapper.writeValueAsString(original);
        DashboardBackoffice deserializado = mapper.readValue(json, DashboardBackoffice.class);

        assertThat(deserializado.itensCriticosAbertosMais48h()).isEqualTo(1L);
        assertThat(deserializado.recebimentosDoDia()).isEqualByComparingTo("250.00");
        assertThat(deserializado.inadimplenciaTotal().numeroParcelas()).isEqualTo(2);
        assertThat(deserializado.geradoEm()).isEqualTo(original.geradoEm());
    }
}
