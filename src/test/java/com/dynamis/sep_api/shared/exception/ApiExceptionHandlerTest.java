package com.dynamis.sep_api.shared.exception;

import com.dynamis.sep_api.identity.application.exception.ContaBloqueadaException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

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
     * Sprint 34 Task 34.3: o {@code 423} publica o tempo restante no {@code Retry-After}. Os dois
     * numeros divergem de proposito — a {@code message} enuncia a politica (30 minutos) e o header
     * diz quando voltar (615s) —, entao um header derivado da duracao configurada fica vermelho.
     */
    @Test
    void contaBloqueadaMapeiaPara423ComRetryAfterDoTempoRestante() {
        Mockito.when(request.getRequestURI()).thenReturn("/api/v1/auth/login");
        ContaBloqueadaException ex = new ContaBloqueadaException(30, 615);

        ResponseEntity<ErrorResponseDto> response = handler.handleLocked(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.LOCKED);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("615");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(423);
        assertThat(response.getBody().message()).contains("30");
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
