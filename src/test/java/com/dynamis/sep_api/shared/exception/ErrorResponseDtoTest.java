package com.dynamis.sep_api.shared.exception;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

import java.time.OffsetDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrato de serializacao do corpo de erro (Sprint 36 Task 36.1).
 *
 * <p><b>Por que {@code @JsonTest} e nao {@code new ObjectMapper()}</b>: o mapper cru nao e o mapper
 * do fio. O Boot desliga {@code WRITE_DATES_AS_TIMESTAMPS} e registra o {@code JavaTimeModule}, e
 * um teste montado sobre mapper proprio ja mentiu neste projeto sobre como {@code Duration} sai na
 * resposta. A fatia {@code @JsonTest} entrega o mapper configurado sem subir contexto inteiro.
 *
 * <p>O par de testes cobre as duas formas do corpo. A ausencia importa tanto quanto a presenca: o
 * campo e opcional, e {@code codigo: null} no JSON quebraria consumidor que hoje ramifica por
 * {@code 'codigo' in body}.
 */
@JsonTest
class ErrorResponseDtoTest {

    private static final OffsetDateTime INSTANTE = OffsetDateTime.parse("2026-09-08T10:15:30-03:00");

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void corpoComCodigoSerializaOCampo() throws Exception {
        ErrorResponseDto corpo = new ErrorResponseDto(
                INSTANTE, 423, "Locked", "Conta bloqueada", "/api/v1/auth/login", "trace-1", "AUTH-423-001");

        String json = objectMapper.writeValueAsString(corpo);

        assertThat(objectMapper.readTree(json).path("codigo").asText()).isEqualTo("AUTH-423-001");
    }

    /**
     * O {@code @JsonInclude(NON_NULL)} da classe ja cobre este caso — e nada provava que cobria. Um
     * corpo legado tem de sair com as mesmas seis propriedades de antes da sprint, sem a setima.
     */
    @Test
    void corpoSemCodigoOmiteAPropriedade() throws Exception {
        ErrorResponseDto corpo =
                ErrorResponseDto.of(400, "Bad Request", "Requisicao invalida", "/api/v1/usuarios", "trace-2");

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(corpo));

        assertThat(json.has("codigo")).isFalse();
        assertThat(json.properties())
                .extracting(Map.Entry::getKey)
                .containsExactlyInAnyOrder("timestamp", "status", "error", "message", "path", "traceId");
    }

    /** A fabrica de seis argumentos e a que a Task 36.2 usa; a de cinco preserva os call sites atuais. */
    @Test
    void fabricaComCodigoPreencheOCampoEAFabricaLegadaODeixaNulo() {
        assertThat(ErrorResponseDto.of(400, "Bad Request", "m", "/p", "t", "USR-400-001")
                        .codigo())
                .isEqualTo("USR-400-001");
        assertThat(ErrorResponseDto.of(400, "Bad Request", "m", "/p", "t").codigo())
                .isNull();
    }
}
