package com.dynamis.sep_api.shared.exception;

import com.dynamis.sep_api.shared.integration.CorrelationIdFilter;
import com.dynamis.sep_api.usuarios.application.exception.SenhaAtualIncorretaException;
import com.dynamis.sep_api.usuarios.application.exception.UsuarioNaoEncontradoException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerCompletoTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();
    private final HttpServletRequest request = Mockito.mock(HttpServletRequest.class);

    @BeforeEach
    void setUp() {
        Mockito.when(request.getRequestURI()).thenReturn("/api/v1/test");
        MDC.put(CorrelationIdFilter.MDC_KEY, "trace-abc");
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void bodyInvalidoMapeiaPara400() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("bad", (Throwable) null, null);

        ResponseEntity<ErrorResponseDto> response = handler.handleUnreadableBody(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(400);
        assertThat(response.getBody().traceId()).isEqualTo("trace-abc");
        assertThat(response.getBody().path()).isEqualTo("/api/v1/test");
    }

    @Test
    void usernameDuplicadoRetorna409ComMensagemAmigavel() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "constraint",
                new RuntimeException("duplicate key value violates unique constraint usuario_username_key"));

        ResponseEntity<ErrorResponseDto> response = handler.handleDataIntegrity(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Username ja cadastrado");
    }

    @Test
    void violacaoDeOutraConstraintRetornaMensagemGenerica() {
        DataIntegrityViolationException ex =
                new DataIntegrityViolationException("constraint", new RuntimeException("not_null violation"));

        ResponseEntity<ErrorResponseDto> response = handler.handleDataIntegrity(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Operacao viola constraint do banco");
    }

    @Test
    void usuarioNaoEncontradoRetorna404() {
        UsuarioNaoEncontradoException ex = new UsuarioNaoEncontradoException(UUID.randomUUID());

        ResponseEntity<ErrorResponseDto> response = handler.handleDomain(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(404);
    }

    @Test
    void senhaAtualIncorretaRetorna400() {
        SenhaAtualIncorretaException ex = new SenhaAtualIncorretaException();

        ResponseEntity<ErrorResponseDto> response = handler.handleDomain(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Senha atual incorreta");
    }

    @Test
    void accessDeniedRetorna403() {
        AccessDeniedException ex = new AccessDeniedException("no");

        ResponseEntity<ErrorResponseDto> response = handler.handleAccessDenied(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Acesso negado");
    }

    @Test
    void badCredentialsRetorna401() {
        BadCredentialsException ex = new BadCredentialsException("bad");

        ResponseEntity<ErrorResponseDto> response = handler.handleAuth(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Autenticacao requerida");
    }

    @Test
    void excecaoGenericaRetorna500SemStacktrace() {
        Exception ex = new RuntimeException("explosao interna");

        ResponseEntity<ErrorResponseDto> response = handler.handleGeneric(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Erro interno. Consulte o suporte com o traceId.");
        assertThat(response.getBody().message()).doesNotContain("RuntimeException");
        assertThat(response.getBody().message()).doesNotContain("at com.dynamis");
    }
}
