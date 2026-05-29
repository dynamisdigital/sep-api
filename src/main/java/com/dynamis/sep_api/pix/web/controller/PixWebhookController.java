package com.dynamis.sep_api.pix.web.controller;

import com.dynamis.sep_api.pix.application.usecase.ProcessarWebhookPixUseCase;
import com.dynamis.sep_api.shared.application.port.out.WebhookSignatureValidator;
import com.dynamis.sep_api.shared.exception.ErrorResponseDto;
import com.dynamis.sep_api.shared.exception.ValidacaoException;
import com.dynamis.sep_api.shared.integration.CorrelationIdFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receptor do webhook Pix/Celcoin (Sprint 19 Task 19.5). Rota literal
 * {@code /api/v1/webhooks/celcoin/pix} tem precedencia sobre o {@code WebhookController} generico.
 *
 * <p>Valida HMAC SHA-256 do payload via {@link WebhookSignatureValidator} (secret
 * {@code app.webhooks.secrets.celcoin-pix}); o processamento idempotente fica em
 * {@link ProcessarWebhookPixUseCase}. A idempotencia usa o {@code event_id} do payload (nao o
 * header), entao nenhum {@code Idempotency-Key} e exigido.
 *
 * <p>Header de assinatura {@code X-Webhook-Signature} (alias {@code X-Celcoin-Signature}).
 */
@RestController
@Tag(name = "webhooks", description = "Webhook Pix Celcoin (HMAC + idempotencia)")
public class PixWebhookController {

    static final String CODIGO_HEADER_OBRIGATORIO = "PIX-400-002";
    static final String PROVIDER_HMAC = "celcoin-pix";

    private final WebhookSignatureValidator signatureValidator;
    private final ProcessarWebhookPixUseCase processarUseCase;

    public PixWebhookController(
            WebhookSignatureValidator signatureValidator, ProcessarWebhookPixUseCase processarUseCase) {
        this.signatureValidator = signatureValidator;
        this.processarUseCase = processarUseCase;
    }

    @PostMapping(path = "/api/v1/webhooks/celcoin/pix", consumes = "application/json")
    @Operation(
            summary = "Receber webhook Pix Celcoin",
            description =
                    "Valida HMAC SHA-256 do payload, registra o evento de forma idempotente (por event_id) sem persistir o payload bruto (apenas hash) e roteia por tipo: recebimento cria PixRecebimento inicial; status de transferencia e reconhecido; tipo desconhecido vira IGNORADO. Nao concilia parcela nem dispara desembolso nesta fase.")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Webhook aceito (novo ou duplicado idempotente)"),
        @ApiResponse(
                responseCode = "400",
                description = "Header de assinatura/body ausente ou payload invalido",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "401",
                description = "Assinatura HMAC invalida",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<Void> receber(
            @RequestHeader(value = "X-Webhook-Signature", required = false) String signaturePadrao,
            @RequestHeader(value = "X-Celcoin-Signature", required = false) String signatureAlias,
            @RequestBody String payload) {

        String signature = signaturePadrao != null && !signaturePadrao.isBlank() ? signaturePadrao : signatureAlias;

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

        processarUseCase.executar(payload, MDC.get(CorrelationIdFilter.MDC_KEY));
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }
}
