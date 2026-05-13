package com.dynamis.sep_api.onboarding.web.controller;

import com.dynamis.sep_api.onboarding.application.usecase.ProcessarCallbackKycUseCase;
import com.dynamis.sep_api.onboarding.web.dto.CelcoinKycCallbackRequest;
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
 * Receptor do webhook Celcoin KYC. Rota literal {@code /api/v1/webhooks/celcoin/kyc} tem
 * precedencia sobre o {@code WebhookController} generico ({@code /{provider}/{event}}) — Spring
 * {@code RequestMappingHandlerMapping} resolve por especificidade.
 *
 * <p>Reusa Sprint 4 ({@link WebhookSignatureValidator} para HMAC, {@link
 * com.dynamis.sep_api.shared.application.usecase.RegistrarWebhookEventUseCase} para outbox dentro
 * do use case dedicado).
 *
 * <p>HMAC: {@code app.webhooks.secrets.celcoin-kyc}. Header de assinatura padrao
 * {@code X-Webhook-Signature}; alias {@code X-Celcoin-Signature} aceito quando a sandbox usar esse
 * nome.
 */
@RestController
@Tag(name = "webhooks", description = "Webhook KYC do Celcoin Onboarding (HMAC + idempotencia)")
public class CelcoinKycWebhookController {

    static final String CODIGO_HEADER_OBRIGATORIO = "ONB-400-006";
    static final String PROVIDER_HMAC = "celcoin-kyc";

    private final WebhookSignatureValidator signatureValidator;
    private final ProcessarCallbackKycUseCase processarCallbackUseCase;
    private final ObjectMapper objectMapper;

    public CelcoinKycWebhookController(
            WebhookSignatureValidator signatureValidator,
            ProcessarCallbackKycUseCase processarCallbackUseCase,
            ObjectMapper objectMapper) {
        this.signatureValidator = signatureValidator;
        this.processarCallbackUseCase = processarCallbackUseCase;
        this.objectMapper = objectMapper;
    }

    @PostMapping(path = "/api/v1/webhooks/celcoin/kyc", consumes = "application/json")
    @Operation(
            summary = "Receber callback KYC Celcoin",
            description =
                    "Valida HMAC SHA-256 do payload via WebhookSignatureValidator (Sprint 4), grava o evento no outbox e processa o resultado da verificacao. Idempotente por Idempotency-Key.")
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

        CelcoinKycCallbackRequest body = parsearPayload(payload);
        processarCallbackUseCase.executar(
                idempotencyKey,
                signature,
                payload,
                new ProcessarCallbackKycUseCase.CelcoinKycCallback(body.idVerificacao(), body.status(), body.reason()));

        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    private CelcoinKycCallbackRequest parsearPayload(String payload) {
        try {
            return objectMapper.readValue(payload, CelcoinKycCallbackRequest.class);
        } catch (JsonProcessingException ex) {
            throw new ValidacaoException(CODIGO_HEADER_OBRIGATORIO, "Body do webhook nao e JSON valido");
        }
    }
}
