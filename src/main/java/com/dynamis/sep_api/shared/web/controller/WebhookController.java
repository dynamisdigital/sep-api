package com.dynamis.sep_api.shared.web.controller;

import com.dynamis.sep_api.shared.application.port.out.WebhookSignatureValidator;
import com.dynamis.sep_api.shared.application.usecase.RegistrarWebhookEventUseCase;
import com.dynamis.sep_api.shared.exception.ErrorResponseDto;
import com.dynamis.sep_api.shared.exception.ValidacaoException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/webhooks")
@Tag(name = "webhooks", description = "Receptor generico de webhooks (HMAC + idempotencia)")
public class WebhookController {

    private static final String CODIGO_HEADER_OBRIGATORIO = "WHK-400-002";

    private final WebhookSignatureValidator signatureValidator;
    private final RegistrarWebhookEventUseCase registrarWebhookEventUseCase;

    public WebhookController(
            WebhookSignatureValidator signatureValidator, RegistrarWebhookEventUseCase registrarWebhookEventUseCase) {
        this.signatureValidator = signatureValidator;
        this.registrarWebhookEventUseCase = registrarWebhookEventUseCase;
    }

    @PostMapping(path = "/{provider}/{event}", consumes = "application/json")
    @Operation(
            summary = "Receber webhook externo",
            description =
                    "Valida assinatura HMAC-SHA256 e registra o evento como PENDENTE no outbox. Idempotente por Idempotency-Key.")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Evento aceito (novo ou duplicado idempotente)"),
        @ApiResponse(
                responseCode = "400",
                description = "Headers obrigatorios ausentes ou body invalido",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "401",
                description = "Assinatura invalida",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<Void> receber(
            @Parameter(example = "celcoin") @PathVariable String provider,
            @Parameter(example = "pagamento_recebido") @PathVariable String event,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Webhook-Signature", required = false) String signature,
            @RequestBody String payload) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ValidacaoException(CODIGO_HEADER_OBRIGATORIO, "Header Idempotency-Key e obrigatorio");
        }
        if (signature == null || signature.isBlank()) {
            throw new ValidacaoException(CODIGO_HEADER_OBRIGATORIO, "Header X-Webhook-Signature e obrigatorio");
        }
        if (payload == null || payload.isBlank()) {
            throw new ValidacaoException(CODIGO_HEADER_OBRIGATORIO, "Body do webhook e obrigatorio");
        }

        if (!signatureValidator.isValid(provider, payload, signature)) {
            throw new BadCredentialsException("Assinatura de webhook invalida");
        }

        registrarWebhookEventUseCase.executar(provider, event, idempotencyKey, signature, payload);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }
}
