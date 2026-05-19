package com.dynamis.sep_api.credito.web.controller;

import com.dynamis.sep_api.credito.application.usecase.ProcessarWebhookOpenFinanceUseCase;
import com.dynamis.sep_api.credito.web.dto.CelcoinOpenFinanceCallbackRequest;
import com.dynamis.sep_api.shared.application.port.out.WebhookSignatureValidator;
import com.dynamis.sep_api.shared.exception.ErrorResponseDto;
import com.dynamis.sep_api.shared.exception.ValidacaoException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receptor do webhook Celcoin Open Finance (Sprint 9 Task 9.5). Rota literal
 * {@code /api/v1/webhooks/celcoin/open-finance} tem precedencia sobre {@code
 * WebhookController} generico ({@code /{provider}/{event}}) — Spring resolve por especificidade.
 *
 * <p>Reusa padrao Sprint 4/6/7: {@link WebhookSignatureValidator} pra HMAC + outbox via
 * {@link ProcessarWebhookOpenFinanceUseCase}.
 *
 * <p>HMAC: {@code app.webhooks.secrets.celcoin-open-finance}. Header padrao
 * {@code X-Webhook-Signature}; alias {@code X-Celcoin-Signature} aceito.
 */
@RestController
@Tag(name = "webhooks", description = "Webhook Celcoin Open Finance (HMAC + idempotencia)")
public class CelcoinOpenFinanceWebhookController {

    static final String CODIGO_HEADER_OBRIGATORIO = "OF-400-001";
    static final String PROVIDER_HMAC = "celcoin-open-finance";

    private final WebhookSignatureValidator signatureValidator;
    private final ProcessarWebhookOpenFinanceUseCase processarUseCase;
    private final ObjectMapper objectMapper;

    public CelcoinOpenFinanceWebhookController(
            WebhookSignatureValidator signatureValidator,
            ProcessarWebhookOpenFinanceUseCase processarUseCase,
            ObjectMapper objectMapper) {
        this.signatureValidator = signatureValidator;
        this.processarUseCase = processarUseCase;
        this.objectMapper = objectMapper;
    }

    @PostMapping(path = "/api/v1/webhooks/celcoin/open-finance", consumes = "application/json")
    @Operation(
            summary = "Receber callback Open Finance Celcoin",
            description =
                    "Valida HMAC SHA-256 do payload via WebhookSignatureValidator (Sprint 4), grava o evento no outbox e roteia por tipo: consent.authorized/denied/expired -> ProcessarCallbackConsentimentoUseCase; movement.data -> ack (consulta real e PULL pos autorizacao). Idempotente por Idempotency-Key.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "202",
                description =
                        "Callback aceito (novo ou duplicado idempotente; status final processado quando aplicavel)"),
        @ApiResponse(
                responseCode = "400",
                description = "Headers obrigatorios ausentes ou body invalido",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "401",
                description = "Assinatura HMAC invalida",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<Void> receber(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Webhook-Signature", required = false) String signaturePadrao,
            @RequestHeader(value = "X-Celcoin-Signature", required = false) String signatureAlias,
            @RequestBody String payload) {

        String signature = signaturePadrao != null && !signaturePadrao.isBlank() ? signaturePadrao : signatureAlias;

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ValidacaoException(CODIGO_HEADER_OBRIGATORIO, "Header Idempotency-Key e obrigatorio");
        }
        if (signature == null || signature.isBlank()) {
            throw new ValidacaoException(
                    CODIGO_HEADER_OBRIGATORIO, "Header X-Webhook-Signature (ou X-Celcoin-Signature) e obrigatorio");
        }
        if (payload == null || payload.isBlank()) {
            throw new ValidacaoException(CODIGO_HEADER_OBRIGATORIO, "Body do webhook e obrigatorio");
        }

        if (!signatureValidator.isValid(PROVIDER_HMAC, payload, signature)) {
            throw new BadCredentialsException("Assinatura de webhook invalida");
        }

        CelcoinOpenFinanceCallbackRequest body = parsearPayload(payload);
        processarUseCase.executar(
                idempotencyKey,
                signature,
                payload,
                new ProcessarWebhookOpenFinanceUseCase.CallbackOpenFinance(
                        body.tipo(), body.consentId(), body.motivo()));

        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    private CelcoinOpenFinanceCallbackRequest parsearPayload(String payload) {
        try {
            return objectMapper.readValue(payload, CelcoinOpenFinanceCallbackRequest.class);
        } catch (JsonProcessingException ex) {
            throw new ValidacaoException(CODIGO_HEADER_OBRIGATORIO, "Body do webhook nao e JSON valido");
        }
    }
}
