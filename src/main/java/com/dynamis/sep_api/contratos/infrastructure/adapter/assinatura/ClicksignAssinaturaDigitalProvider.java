package com.dynamis.sep_api.contratos.infrastructure.adapter.assinatura;

import com.dynamis.sep_api.contratos.application.port.out.AssinaturaDigitalProvider;
import com.dynamis.sep_api.contratos.application.port.out.dto.RequisicaoEnvioAssinatura;
import com.dynamis.sep_api.contratos.application.port.out.dto.RespostaEnvioAssinatura;
import com.dynamis.sep_api.contratos.application.port.out.dto.StatusEnvelopeProvider;
import com.dynamis.sep_api.contratos.infrastructure.adapter.assinatura.dto.ClicksignDocumentRequest;
import com.dynamis.sep_api.contratos.infrastructure.adapter.assinatura.dto.ClicksignDocumentResponse;
import com.dynamis.sep_api.contratos.infrastructure.adapter.assinatura.dto.ClicksignSignerRequest;
import com.dynamis.sep_api.shared.integration.RestClientFactory;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Base64;
import java.util.Objects;

/**
 * Adapter HTTP real do {@link AssinaturaDigitalProvider} para a Clicksign (Sprint 11 Task 11.4 /
 * ADR 0013). Ativado quando {@code app.assinatura.provider=clicksign}.
 *
 * <p>Fluxo:
 * <ul>
 *   <li>{@code enviarParaAssinatura}: POST {@code /api/v1/documents} com PDF base64; depois POST
 *       {@code /api/v1/lists} vinculando signatario. {@code idempotencyKey} vai no header
 *       {@code Idempotency-Key}. Resposta carrega {@code document.key} = idEnvelopeExterno.
 *   <li>{@code baixarDocumentoAssinado}: GET {@code /api/v1/documents/{key}/download} —
 *       endpoint simplificado pra teste de wiring (sandbox real expoe signed_file_url separado;
 *       limitacao documentada em CCB.md).
 *   <li>{@code consultarStatus}: GET {@code /api/v1/documents/{key}}; mapper traduz status nativo.
 * </ul>
 *
 * <p>Resilience4j (instance {@code clicksign-assinatura}): retry em 5xx/IOException +
 * circuit breaker. Timeouts via {@link RestClientFactory}.
 *
 * <p>LGPD: logs nao incluem body de erro nem PDF — apenas status HTTP + correlationId +
 * idEnvelopeExterno.
 */
@Component
@ConditionalOnProperty(name = "app.assinatura.provider", havingValue = "clicksign", matchIfMissing = false)
public class ClicksignAssinaturaDigitalProvider implements AssinaturaDigitalProvider {

    private static final Logger log = LoggerFactory.getLogger(ClicksignAssinaturaDigitalProvider.class);
    static final String RESILIENCE_INSTANCE = "clicksign-assinatura";

    private final RestClient restClient;
    private final ClicksignAssinaturaProperties properties;
    private final ClicksignAssinaturaMapper mapper;

    public ClicksignAssinaturaDigitalProvider(
            RestClientFactory factory, ClicksignAssinaturaProperties properties, ClicksignAssinaturaMapper mapper) {
        Objects.requireNonNull(properties.baseUrl(), "app.assinatura.clicksign.base-url obrigatorio");
        if (properties.accessToken() == null || properties.accessToken().isBlank()) {
            throw new IllegalStateException(
                    "app.assinatura.clicksign.access-token obrigatorio quando app.assinatura.provider=clicksign");
        }
        this.restClient = factory.forProvider(RESILIENCE_INSTANCE, properties.baseUrl());
        this.properties = properties;
        this.mapper = mapper;
    }

    @Override
    @CircuitBreaker(name = RESILIENCE_INSTANCE)
    @Retry(name = RESILIENCE_INSTANCE)
    public RespostaEnvioAssinatura enviarParaAssinatura(
            byte[] pdf, RequisicaoEnvioAssinatura req, String correlationId) {
        Objects.requireNonNull(pdf, "pdf obrigatorio");
        try (MDCBridge ignored = MDCBridge.set(correlationId)) {
            String dataUrl =
                    "data:application/pdf;base64," + Base64.getEncoder().encodeToString(pdf);
            ClicksignDocumentRequest body = new ClicksignDocumentRequest(new ClicksignDocumentRequest.Document(
                    "/contratos/" + req.contratoId() + "/CCB.pdf", dataUrl, null));
            ClicksignDocumentResponse resp = restClient
                    .post()
                    .uri("/api/v1/documents")
                    .headers(h -> headersComIdempotency(h, req.idempotencyKey()))
                    .body(body)
                    .retrieve()
                    .body(ClicksignDocumentResponse.class);
            if (resp == null || resp.document() == null || resp.document().key() == null) {
                throw new ClicksignRespostaInvalidaException("Resposta sem document.key");
            }
            String key = resp.document().key();
            vincularSignatario(key, req);
            log.info(
                    "Clicksign envio OK contratoId={} idEnvelopeExterno={} correlationId={}",
                    req.contratoId(),
                    key,
                    correlationId);
            return new RespostaEnvioAssinatura(
                    key, mapper.parseUpdatedAt(resp.document().updatedAt()));
        } catch (RestClientResponseException ex) {
            log.warn(
                    "Clicksign enviar falhou status={} correlationId={}",
                    ex.getStatusCode().value(),
                    correlationId);
            throw ex;
        }
    }

    /**
     * Vincula o signatario ao documento no provider. Usa o MESMO {@code idempotencyKey} do envio
     * pra preservar contrato unico de idempotencia da Task 11.5 (Idempotency-Key derivado de
     * {@code contratoId + numeroVersao}). Retry parcial (documents OK, lists falhou) reusa a
     * chave; Clicksign cobre dedup interna.
     *
     * <p>{@code retrieve().toBodilessEntity()} propaga {@link RestClientResponseException} em
     * 4xx/5xx (RestClient default), o que aciona Resilience4j retry no metodo publico
     * {@code enviarParaAssinatura}; nao engole erros.
     */
    private void vincularSignatario(String documentKey, RequisicaoEnvioAssinatura req) {
        ClicksignSignerRequest body = new ClicksignSignerRequest(new ClicksignSignerRequest.List(
                documentKey, req.signatarioEmail(), req.signatarioNome(), "contractor"));
        restClient
                .post()
                .uri("/api/v1/lists")
                .headers(h -> headersComIdempotency(h, req.idempotencyKey()))
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    @CircuitBreaker(name = RESILIENCE_INSTANCE)
    @Retry(name = RESILIENCE_INSTANCE)
    public byte[] baixarDocumentoAssinado(String idEnvelopeExterno) {
        Objects.requireNonNull(idEnvelopeExterno, "idEnvelopeExterno obrigatorio");
        byte[] bytes = restClient
                .get()
                .uri("/api/v1/documents/{key}/download", idEnvelopeExterno)
                .headers(this::headersBase)
                .retrieve()
                .body(byte[].class);
        if (bytes == null || bytes.length == 0) {
            throw new ClicksignRespostaInvalidaException("PDF assinado vazio");
        }
        return bytes;
    }

    @Override
    @CircuitBreaker(name = RESILIENCE_INSTANCE)
    @Retry(name = RESILIENCE_INSTANCE)
    public StatusEnvelopeProvider consultarStatus(String idEnvelopeExterno) {
        Objects.requireNonNull(idEnvelopeExterno, "idEnvelopeExterno obrigatorio");
        ClicksignDocumentResponse resp = restClient
                .get()
                .uri("/api/v1/documents/{key}", idEnvelopeExterno)
                .headers(this::headersBase)
                .retrieve()
                .body(ClicksignDocumentResponse.class);
        if (resp == null || resp.document() == null) {
            throw new ClicksignRespostaInvalidaException("Resposta sem document");
        }
        return new StatusEnvelopeProvider(
                mapper.toStatusEnvelope(resp.document().status()),
                mapper.parseUpdatedAt(resp.document().updatedAt()));
    }

    private void headersBase(HttpHeaders headers) {
        headers.setBearerAuth(properties.accessToken());
        headers.add("Accept", "application/json");
    }

    private void headersComIdempotency(HttpHeaders headers, String idempotencyKey) {
        headersBase(headers);
        headers.add("Idempotency-Key", idempotencyKey);
    }

    /** Ponte MDC: garante {@code correlationId} no contexto durante a chamada HTTP. */
    private static final class MDCBridge implements AutoCloseable {

        private static final String MDC_KEY = com.dynamis.sep_api.shared.integration.CorrelationIdFilter.MDC_KEY;
        private final String anterior;

        private MDCBridge(String anterior) {
            this.anterior = anterior;
        }

        static MDCBridge set(String correlationId) {
            String prev = MDC.get(MDC_KEY);
            if (correlationId != null && !correlationId.isBlank()) {
                MDC.put(MDC_KEY, correlationId);
            }
            return new MDCBridge(prev);
        }

        @Override
        public void close() {
            if (anterior == null) {
                MDC.remove(MDC_KEY);
            } else {
                MDC.put(MDC_KEY, anterior);
            }
        }
    }
}
