package com.dynamis.sep_api.shared.exception;

import com.dynamis.sep_api.contratos.application.port.out.exception.AssinaturaProviderException;
import com.dynamis.sep_api.contratos.application.port.out.exception.AssinaturaProviderHttpException;
import com.dynamis.sep_api.contratos.application.port.out.exception.EnvelopeNaoEncontradoException;
import com.dynamis.sep_api.identity.application.exception.ContaBloqueadaException;
import com.dynamis.sep_api.pix.application.port.out.exception.PixProviderException;
import com.dynamis.sep_api.pix.application.port.out.exception.PixProviderHttpException;
import com.dynamis.sep_api.shared.integration.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Tratamento centralizado de erros da API SEP — handler consolidado da Sprint 4.
 *
 * <p>Atende PRD §13 (Padrao de Erros da API). Mapeia validacao, autenticacao, autorizacao,
 * {@link DomainException} e fallback generico para o payload {@link ErrorResponseDto}, sempre
 * preenchendo {@code traceId} a partir do {@code correlationId} no MDC via
 * {@link CorrelationIdFilter#MDC_KEY}.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private static final String FALLBACK_500_MESSAGE = "Erro interno. Consulte o suporte com o traceId.";

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

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponseDto> handleMissingRequestHeader(
            MissingRequestHeaderException ex, HttpServletRequest request) {
        String mensagem = "Header obrigatorio ausente: " + ex.getHeaderName();
        return build(HttpStatus.BAD_REQUEST, "Bad Request", mensagem, request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponseDto> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String tipo = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "tipo esperado";
        String mensagem = "Path/query param '" + ex.getName() + "' invalido: nao eh " + tipo;
        return build(HttpStatus.BAD_REQUEST, "Bad Request", mensagem, request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponseDto> handleDataIntegrity(
            DataIntegrityViolationException ex, HttpServletRequest request) {
        String causa = ex.getMostSpecificCause() != null
                ? String.valueOf(ex.getMostSpecificCause().getMessage())
                : "";
        log.warn("Violacao de integridade: {}", causa);
        String mensagem;
        if (causa.contains("usuario_username_key") || causa.toLowerCase().contains("username")) {
            mensagem = "Username ja cadastrado";
        } else {
            mensagem = "Operacao viola constraint do banco";
        }
        return build(HttpStatus.CONFLICT, "Conflict", mensagem, request);
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
        HttpStatus status =
                switch (ex) {
                    case ValidacaoException ignored -> HttpStatus.BAD_REQUEST;
                    case RecursoNaoEncontradoException ignored -> HttpStatus.NOT_FOUND;
                    case ConflitoException ignored -> HttpStatus.CONFLICT;
                    case AcessoNegadoException ignored -> HttpStatus.FORBIDDEN;
                    case OperacaoNaoProcessavelException ignored -> HttpStatus.UNPROCESSABLE_ENTITY;
                };
        return build(status, status.getReasonPhrase(), ex.getMessage(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponseDto> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "Forbidden", "Acesso negado", request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponseDto> handleAuth(AuthenticationException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "Unauthorized", "Autenticacao requerida", request);
    }

    @ExceptionHandler(ContaBloqueadaException.class)
    public ResponseEntity<ErrorResponseDto> handleLocked(ContaBloqueadaException ex, HttpServletRequest request) {
        return build(HttpStatus.LOCKED, "Locked", ex.getMessage(), request);
    }

    /**
     * Traduz falhas do provider de assinatura digital (Sprint 11) para HTTP coerente. Provider 5xx
     * → 503 (operador pode retentar); 4xx → 422 (problema de contrato/permissao no envio, nao
     * recuperavel sem mudar dados); demais (IO, parse, EnvelopeNaoEncontrado) → 502.
     */
    @ExceptionHandler(AssinaturaProviderException.class)
    public ResponseEntity<ErrorResponseDto> handleAssinaturaProvider(
            AssinaturaProviderException ex, HttpServletRequest request) {
        HttpStatus status;
        String error;
        if (ex instanceof AssinaturaProviderHttpException http) {
            if (http.isServerError()) {
                status = HttpStatus.SERVICE_UNAVAILABLE;
                error = "Service Unavailable";
            } else if (http.isClientError()) {
                status = HttpStatus.UNPROCESSABLE_ENTITY;
                error = "Unprocessable Entity";
            } else {
                status = HttpStatus.BAD_GATEWAY;
                error = "Bad Gateway";
            }
        } else if (ex instanceof EnvelopeNaoEncontradoException) {
            status = HttpStatus.BAD_GATEWAY;
            error = "Bad Gateway";
        } else {
            status = HttpStatus.BAD_GATEWAY;
            error = "Bad Gateway";
        }
        log.warn("Falha do provider de assinatura ({}): {}", status, ex.getMessage());
        return build(status, error, ex.getMessage(), request);
    }

    /**
     * Traduz falhas do provider Pix (Sprint 21) para HTTP coerente quando elas propagam ate a borda
     * REST — ex.: criacao de cobranca Pix de recebimento. Provider 5xx → 503 (operador pode
     * retentar); 4xx → 422 (problema de contrato/dados, nao recuperavel sem mudar a requisicao);
     * demais (IO, parse, status desconhecido) → 502. O log nao inclui payload nem dado bancario.
     */
    @ExceptionHandler(PixProviderException.class)
    public ResponseEntity<ErrorResponseDto> handlePixProvider(PixProviderException ex, HttpServletRequest request) {
        HttpStatus status;
        String error;
        if (ex instanceof PixProviderHttpException http && http.isServerError()) {
            status = HttpStatus.SERVICE_UNAVAILABLE;
            error = "Service Unavailable";
        } else if (ex instanceof PixProviderHttpException http && http.isClientError()) {
            status = HttpStatus.UNPROCESSABLE_ENTITY;
            error = "Unprocessable Entity";
        } else {
            status = HttpStatus.BAD_GATEWAY;
            error = "Bad Gateway";
        }
        log.warn("Falha do provider Pix ({}): {}", status, ex.getMessage());
        return build(status, error, ex.getMessage(), request);
    }

    @ExceptionHandler(com.dynamis.sep_api.backoffice.domain.exception.LimiteReprocessoExcedidoException.class)
    public ResponseEntity<ErrorResponseDto> handleLimiteReprocesso(
            com.dynamis.sep_api.backoffice.domain.exception.LimiteReprocessoExcedidoException ex,
            HttpServletRequest request) {
        return build(HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests", ex.getMessage(), request);
    }

    @ExceptionHandler(com.dynamis.sep_api.backoffice.domain.exception.TipoReprocessoNaoSuportadoException.class)
    public ResponseEntity<ErrorResponseDto> handleTipoReprocesso(
            com.dynamis.sep_api.backoffice.domain.exception.TipoReprocessoNaoSuportadoException ex,
            HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Erro nao tratado", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", FALLBACK_500_MESSAGE, request);
    }

    private ResponseEntity<ErrorResponseDto> build(
            HttpStatus status, String error, String message, HttpServletRequest request) {
        ErrorResponseDto body = ErrorResponseDto.of(
                status.value(), error, message, request.getRequestURI(), MDC.get(CorrelationIdFilter.MDC_KEY));
        return ResponseEntity.status(status).body(body);
    }
}
