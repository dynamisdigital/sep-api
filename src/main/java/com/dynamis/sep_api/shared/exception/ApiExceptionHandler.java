package com.dynamis.sep_api.shared.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Tratamento centralizado de erros da API SEP — stub da Sprint 1.
 *
 * <p>Atende PRD §13 (Padrao de Erros da API). Esta versao mapeia apenas as excecoes basicas
 * necessarias para nao expor stack traces. A Sprint 4 evolui com mapeamento completo de
 * validacao, autenticacao, autorizacao, {@link DomainException} e fallback generico.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDto> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        String mensagem = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + " " + fe.getDefaultMessage())
                .findFirst()
                .orElse("Requisicao invalida");
        return build(HttpStatus.BAD_REQUEST, "Bad Request", mensagem, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponseDto> handleUnreadableBody(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Bad Request", "Corpo da requisicao invalido", request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponseDto> handleDataIntegrity(
            DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Violacao de integridade: {}", ex.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT, "Conflict", "Operacao viola constraint do banco", request);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleNotFound(NoHandlerFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "Not Found", "Recurso nao encontrado", request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "Not Found", "Recurso nao encontrado", request);
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ErrorResponseDto> handleDomain(DomainException ex, HttpServletRequest request) {
        // mapping rudimentar - Sprint 4 fara switch exhaustivo na sealed hierarchy
        HttpStatus status =
                switch (ex) {
                    case ValidacaoException ignored -> HttpStatus.BAD_REQUEST;
                    case RecursoNaoEncontradoException ignored -> HttpStatus.NOT_FOUND;
                    case ConflitoException ignored -> HttpStatus.CONFLICT;
                };
        return build(status, status.getReasonPhrase(), ex.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Erro nao tratado", ex);
        return build(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                "Erro interno. Consulte o suporte com o traceId.",
                request);
    }

    private ResponseEntity<ErrorResponseDto> build(
            HttpStatus status, String error, String message, HttpServletRequest request) {
        ErrorResponseDto body =
                ErrorResponseDto.of(status.value(), error, message, request.getRequestURI(), MDC.get("correlationId"));
        return ResponseEntity.status(status).body(body);
    }
}
