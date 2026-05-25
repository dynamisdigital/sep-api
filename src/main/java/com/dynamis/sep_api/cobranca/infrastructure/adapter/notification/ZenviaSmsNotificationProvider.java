package com.dynamis.sep_api.cobranca.infrastructure.adapter.notification;

import com.dynamis.sep_api.cobranca.application.port.out.NotificationProvider;
import com.dynamis.sep_api.cobranca.application.port.out.TemplateNotificacaoEngine;
import com.dynamis.sep_api.cobranca.application.port.out.dto.Notificacao;
import com.dynamis.sep_api.cobranca.application.port.out.dto.ResultadoNotificacao;
import com.dynamis.sep_api.cobranca.domain.vo.CanalNotificacao;
import com.dynamis.sep_api.shared.integration.RestClientFactory;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Adapter Zenvia SMS via REST (Sprint 13 - ADR 0014).
 *
 * <p>Ativado quando {@code app.notificacoes.provider=smtp-zenvia}. Renderiza template TEXT,
 * monta payload conforme contrato Zenvia v2 (POST {@code /v2/channels/sms/messages}), adiciona
 * header {@code X-API-TOKEN} e propaga rastreio.
 *
 * <p>Resilience4j (instance {@code zenvia-sms}): retry em 5xx/IOException, circuit breaker e
 * timeout — configurados em {@code application.yml}.
 *
 * <p>Token vazio em runtime falha rapido no construtor pra evitar {@code Bearer } invalido em
 * producao quando provider esta ativo sem credencial.
 */
@Component
@ConditionalOnProperty(name = "app.notificacoes.provider", havingValue = "smtp-zenvia")
public class ZenviaSmsNotificationProvider implements NotificationProvider {

    private static final Logger log = LoggerFactory.getLogger(ZenviaSmsNotificationProvider.class);
    private static final String NOME = "zenvia";
    private static final String RESILIENCE_INSTANCE = "zenvia-sms";
    // Telefone E.164 simplificado: comeca com '+' opcional, 10-15 digitos.
    private static final Pattern TELEFONE = Pattern.compile("^\\+?\\d{10,15}$");

    private final RestClient restClient;
    private final TemplateNotificacaoEngine templateEngine;
    private final String apiToken;
    private final String from;
    private final Retry retry;
    private final CircuitBreaker circuitBreaker;

    public ZenviaSmsNotificationProvider(
            RestClientFactory factory,
            NotificacaoProperties props,
            TemplateNotificacaoEngine templateEngine,
            RetryRegistry retryRegistry,
            CircuitBreakerRegistry circuitBreakerRegistry) {
        if (props.zenvia().baseUrl() == null || props.zenvia().baseUrl().isBlank()) {
            throw new IllegalStateException("app.notificacoes.zenvia.base-url obrigatorio quando provider=smtp-zenvia");
        }
        if (props.zenvia().apiToken() == null || props.zenvia().apiToken().isBlank()) {
            throw new IllegalStateException(
                    "app.notificacoes.zenvia.api-token obrigatorio quando provider=smtp-zenvia");
        }
        this.restClient =
                factory.forProvider(RESILIENCE_INSTANCE, props.zenvia().baseUrl());
        this.templateEngine = templateEngine;
        this.apiToken = props.zenvia().apiToken();
        this.from = props.zenvia().from();
        // Self-invocation impede que as anotacoes @Retry/@CircuitBreaker funcionem (chamadas
        // diretas via `this` bypassam o proxy AOP). Resilience4j programmatic resolve sem
        // depender de extrair um segundo bean.
        this.retry = retryRegistry.retry(RESILIENCE_INSTANCE);
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker(RESILIENCE_INSTANCE);
    }

    @Override
    public ResultadoNotificacao enviar(Notificacao notificacao) {
        if (!suporta(notificacao.canal())) {
            return ResultadoNotificacao.falha(NOME, "canal nao suportado: " + notificacao.canal());
        }
        if (!TELEFONE.matcher(notificacao.destinatario()).matches()) {
            return ResultadoNotificacao.falha(NOME, "telefone invalido");
        }
        String texto = templateEngine.renderizar(CanalNotificacao.SMS, notificacao.template(), notificacao.variaveis());
        Map<String, Object> payload = Map.of(
                "from",
                from,
                "to",
                notificacao.destinatario(),
                "contents",
                List.of(Map.of("type", "text", "text", texto)));
        try {
            ZenviaResposta resposta = postar(payload);
            String idExterno = resposta != null ? resposta.id() : null;
            log.info("Zenvia SMS enviado idExterno={} correlationId={}", idExterno, notificacao.correlationId());
            return ResultadoNotificacao.sucesso(NOME, idExterno);
        } catch (RestClientResponseException e) {
            // 4xx eh erro de payload — devolve como falha tecnica controlada (use case grava FALHA).
            // 5xx ja sofreu retry pelo Resilience4j em {@link #postar} antes de chegar aqui.
            log.warn(
                    "Zenvia SMS falhou status={} correlationId={}",
                    e.getStatusCode().value(),
                    notificacao.correlationId());
            return ResultadoNotificacao.falha(
                    NOME, "zenvia http " + e.getStatusCode().value());
        }
    }

    /**
     * Chamada HTTP isolada e decorada com retry + circuit breaker via Resilience4j programmatic.
     * Self-invocation a partir de {@link #enviar} nao passa pelo proxy AOP, entao as anotacoes
     * {@code @Retry}/{@code @CircuitBreaker} seriam ignoradas — por isso aplicamos as
     * politicas via {@code Retry.decorateSupplier} + {@code CircuitBreaker.decorateSupplier}.
     */
    private ZenviaResposta postar(Map<String, Object> payload) {
        java.util.function.Supplier<ZenviaResposta> chamada = () -> restClient
                .post()
                .uri("/v2/channels/sms/messages")
                .headers(this::headers)
                .body(payload)
                .retrieve()
                .body(ZenviaResposta.class);
        return Retry.decorateSupplier(retry, CircuitBreaker.decorateSupplier(circuitBreaker, chamada))
                .get();
    }

    @Override
    public boolean suporta(CanalNotificacao canal) {
        return canal == CanalNotificacao.SMS;
    }

    @Override
    public String nome() {
        return NOME;
    }

    private void headers(HttpHeaders headers) {
        headers.set("X-API-TOKEN", apiToken);
        headers.set("Accept", "application/json");
    }

    /** Resposta minima da Zenvia — campo {@code id} eh suficiente pra rastreio. */
    record ZenviaResposta(String id) {}
}
