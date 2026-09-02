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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
        log.atWarn().addKeyValue("event", "data_integrity_violation").log("Violacao de integridade");
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

    /**
     * Metodo HTTP nao suportado na rota (Sprint 35 Task 35.3). Ate aqui caia no {@code Exception} de
     * fallback e virava {@code 500}: o servidor anunciava falha propria para um erro do cliente, e o
     * corpo trazia "Consulte o suporte com o traceId" — pedindo suporte para quem so precisa trocar o
     * verbo.
     *
     * <p>Fica junto dos handlers de roteamento ({@code NoHandlerFoundException},
     * {@code NoResourceFoundException}) porque a natureza e a mesma: a request nao casou nenhum
     * handler. O que muda e que aqui o <b>caminho</b> existe.
     *
     * <p>Emite {@code Allow}, exigido pela RFC 9110 §15.5.6 no {@code 405}, quando o Spring souber
     * quais metodos a rota aceita. <b>Nao</b> entra em {@code app.cors.exposed-headers}, e o motivo
     * que nao envelhece nao e "nenhum front ramifica por ele" — e que o <b>corpo ja carrega a mesma
     * lista</b> em {@code message}, entao um consumidor de browser exibe ou ramifica sem depender de
     * CORS. Expor o header seria superficie sem ganho.
     *
     * <p>Alcance: os {@code permitAll} de API do {@code SecurityConfig} sao por metodo, entao um verbo
     * errado numa rota publica para em {@code 401} antes do dispatcher — medido. O {@code 405}
     * observado por teste e o dos paths cujo matcher nao fixa metodo; para rota autenticada ele
     * decorre do mesmo caminho de dispatch, e isso <b>nao</b> esta coberto por teste.
     *
     * <p>Loga em {@code WARN}, e nao em silencio: antes desta Task o caso passava pelo
     * {@code handleGeneric} e deixava rastro em {@code ERROR}. Erro de cliente nao merece {@code ERROR},
     * mas uma rajada de {@code 405} e justamente o sinal de integracao quebrada — mesmo criterio do
     * {@code handleDataIntegrity}.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponseDto> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        List<HttpMethod> aceitos = metodosAceitos(ex);
        String mensagem = aceitos.isEmpty()
                ? "Metodo " + ex.getMethod() + " nao suportado nesta rota"
                : "Metodo " + ex.getMethod() + " nao suportado nesta rota. Metodos aceitos: "
                        + aceitos.stream().map(HttpMethod::name).collect(Collectors.joining(", "));
        log.atWarn()
                .addKeyValue("event", "method_not_allowed")
                .addKeyValue("metodo", ex.getMethod())
                .addKeyValue("path", request.getRequestURI())
                .log("Metodo HTTP nao suportado na rota");
        ResponseEntity<ErrorResponseDto> resposta =
                build(HttpStatus.METHOD_NOT_ALLOWED, "Method Not Allowed", mensagem, request);
        if (aceitos.isEmpty()) {
            return resposta;
        }
        return ResponseEntity.status(resposta.getStatusCode())
                .headers(resposta.getHeaders())
                .allow(aceitos.toArray(new HttpMethod[0]))
                .body(resposta.getBody());
    }

    /**
     * Ordena uma vez e alimenta <b>o corpo e o header</b> com a mesma lista: sem isso a resposta podia
     * dizer "GET, POST" no {@code message} e {@code Allow: POST,GET}, duas ordens para o mesmo fato.
     *
     * <p>A ordenacao nao existe porque a ordem do Spring seja instavel — nao e: o
     * {@code getSupportedHttpMethods} devolve {@code LinkedHashSet} preenchido na ordem do array
     * interno, e {@code null} quando nao ha metodos. Existe para a mensagem nao mudar quando alguem
     * reordenar os {@code @…Mapping} do controller.
     */
    private static List<HttpMethod> metodosAceitos(HttpRequestMethodNotSupportedException ex) {
        Set<HttpMethod> suportados = ex.getSupportedHttpMethods();
        return suportados == null
                ? List.of()
                : suportados.stream()
                        .sorted(Comparator.comparing(HttpMethod::name))
                        .toList();
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

    /**
     * {@code Retry-After} com o tempo <b>restante</b> do bloqueio (Sprint 34 Task 34.3). O header e
     * acrescentado aqui, e nao no {@code build}, porque aquele helper e compartilhado por todos os
     * handlers e so o {@code 423} tem essa informacao.
     *
     * <p>A {@code message} continua anunciando a duracao configurada — ela enuncia a politica, o
     * header diz quando voltar.
     *
     * <p>{@code headers(...)} propaga o que o {@code build} tiver posto: hoje ele nao poe nenhum
     * header, mas sem isso o {@code 423} seria o unico erro a perder qualquer header que o helper
     * comum ganhasse depois — e nenhum teste pegaria.
     */
    @ExceptionHandler(ContaBloqueadaException.class)
    public ResponseEntity<ErrorResponseDto> handleLocked(ContaBloqueadaException ex, HttpServletRequest request) {
        ResponseEntity<ErrorResponseDto> resposta = build(HttpStatus.LOCKED, "Locked", ex.getMessage(), request);
        return ResponseEntity.status(resposta.getStatusCode())
                .headers(resposta.getHeaders())
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(segundosAteLiberar(ex.getTempoRestante())))
                .body(resposta.getBody());
    }

    /**
     * Arredonda para cima porque {@code delay-seconds} da RFC 9110 e inteiro: truncar convidaria o
     * cliente a voltar ainda dentro do bloqueio e, com menos de um segundo restante, produziria um
     * {@code Retry-After: 0}.
     *
     * <p>Fica no handler, e nao no dominio ou no servico, porque "segundos inteiros" e exigencia do
     * cabecalho HTTP, nao da politica de lockout.
     */
    static long segundosAteLiberar(Duration restante) {
        return restante.toSeconds() + (restante.toNanosPart() > 0 ? 1 : 0);
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
        log.atWarn()
                .addKeyValue("event", "provider_failed")
                .addKeyValue("provider", "assinatura")
                .addKeyValue("status", status.value())
                .addKeyValue("exceptionType", ex.getClass().getSimpleName())
                .log("Falha do provider de assinatura");
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
        log.atWarn()
                .addKeyValue("event", "provider_failed")
                .addKeyValue("provider", "pix")
                .addKeyValue("status", status.value())
                .addKeyValue("exceptionType", ex.getClass().getSimpleName())
                .log("Falha do provider Pix");
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
        log.atError().addKeyValue("event", "unhandled_exception").setCause(ex).log("Erro nao tratado");
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", FALLBACK_500_MESSAGE, request);
    }

    private ResponseEntity<ErrorResponseDto> build(
            HttpStatus status, String error, String message, HttpServletRequest request) {
        ErrorResponseDto body = ErrorResponseDto.of(
                status.value(), error, message, request.getRequestURI(), MDC.get(CorrelationIdFilter.MDC_KEY));
        return ResponseEntity.status(status).body(body);
    }
}
