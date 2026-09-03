package com.dynamis.sep_api.shared.exception;

import com.dynamis.sep_api.identity.application.exception.ContaBloqueadaException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobertura unitaria do {@link ApiExceptionHandler} stub. Sprint 4 vai estender com cenarios
 * completos.
 */
class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();
    private final HttpServletRequest request = Mockito.mock(HttpServletRequest.class);

    @Test
    void domainValidacaoMapeiaPara400() {
        Mockito.when(request.getRequestURI()).thenReturn("/api/v1/test");
        ValidacaoException ex = new ValidacaoException("VAL_001", "campo invalido");

        ResponseEntity<ErrorResponseDto> response = handler.handleDomain(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(400);
        assertThat(response.getBody().message()).isEqualTo("campo invalido");
        assertThat(response.getBody().path()).isEqualTo("/api/v1/test");
    }

    /**
     * O arredondamento e para cima porque {@code delay-seconds} da RFC 9110 e inteiro. Truncar
     * devolveria {@code Retry-After: 0} para um bloqueio com menos de um segundo restante — um
     * convite a retentar dentro do proprio bloqueio. Os tres casos matam as duas mutacoes:
     * truncamento puro morre no primeiro, {@code +1} incondicional morre no terceiro.
     *
     * <p>Testado aqui, e nao pelo caminho do {@code LockoutService}, porque la o restante sai de
     * {@code OffsetDateTime.now()} e nenhuma assercao de sub-segundo seria estavel.
     */
    @Test
    void retryAfterArredondaParaCimaENuncaDevolveZero() {
        assertThat(ApiExceptionHandler.segundosAteLiberar(Duration.ofMillis(1))).isEqualTo(1);
        assertThat(ApiExceptionHandler.segundosAteLiberar(Duration.ofMillis(1500)))
                .isEqualTo(2);
        assertThat(ApiExceptionHandler.segundosAteLiberar(Duration.ofSeconds(600)))
                .isEqualTo(600);
    }

    /**
     * Sprint 34 Task 34.3: o {@code 423} publica o tempo restante no {@code Retry-After}. A Sprint 35
     * Task 35.7 fez o <b>corpo</b> anunciar o mesmo bloqueio, entao os dois numeros deixaram de
     * divergir: 615s no header e 11 minutos na frase. Um header derivado da duracao configurada
     * continua ficando vermelho aqui.
     */
    @Test
    void contaBloqueadaMapeiaPara423ComRetryAfterDoTempoRestante() {
        Mockito.when(request.getRequestURI()).thenReturn("/api/v1/auth/login");
        ContaBloqueadaException ex = new ContaBloqueadaException(Duration.ofSeconds(615));

        ResponseEntity<ErrorResponseDto> response = handler.handleLocked(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.LOCKED);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("615");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(423);
        assertThat(response.getBody().message())
                .as("Sprint 35 Task 35.7: corpo e header passam a anunciar o MESMO bloqueio — 615s"
                        + " arredondado para cima sao 11 minutos. Antes o corpo dizia 30, a politica"
                        + " configurada, e um consumidor que so lesse o corpo esperava 3x demais")
                .isEqualTo("Conta bloqueada temporariamente. Tente novamente em 11 minutos.");
        assertThat(response.getBody().path()).isEqualTo("/api/v1/auth/login");
    }

    @Test
    void domainNaoEncontradoMapeiaPara404() {
        Mockito.when(request.getRequestURI()).thenReturn("/api/v1/test");
        RecursoNaoEncontradoException ex = new RecursoNaoEncontradoException("REC_001", "nao existe");

        ResponseEntity<ErrorResponseDto> response = handler.handleDomain(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(404);
    }

    @Test
    void domainConflitoMapeiaPara409() {
        Mockito.when(request.getRequestURI()).thenReturn("/api/v1/test");
        ConflitoException ex = new ConflitoException("CON_001", "duplicado");

        ResponseEntity<ErrorResponseDto> response = handler.handleDomain(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(409);
    }

    @Test
    void excecaoGenericaMapeiaPara500SemStackTrace() {
        Mockito.when(request.getRequestURI()).thenReturn("/api/v1/test");
        Exception ex = new RuntimeException("erro interno qualquer");

        ResponseEntity<ErrorResponseDto> response = handler.handleGeneric(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).doesNotContain("RuntimeException");
        assertThat(response.getBody().message()).doesNotContain("at com.dynamis");
    }
}
