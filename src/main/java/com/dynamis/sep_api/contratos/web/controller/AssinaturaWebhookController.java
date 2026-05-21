package com.dynamis.sep_api.contratos.web.controller;

import com.dynamis.sep_api.contratos.application.usecase.ProcessarWebhookAssinaturaUseCase;
import com.dynamis.sep_api.contratos.web.dto.AssinaturaCallbackClicksign;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;

/**
 * Receptor de webhooks de assinatura digital (Sprint 11 Task 11.6). Path param {@code {provider}}
 * suporta multiplos providers paralelos (Clicksign hoje; D4Sign futuro via Provider Pattern).
 *
 * <p>Padrao Sprint 4/6/7/9: valida HMAC via {@link WebhookSignatureValidator}, registra outbox e
 * roteia para {@link ProcessarWebhookAssinaturaUseCase}.
 *
 * <p>Especificidades Clicksign (ADR 0013):
 * <ul>
 *   <li>Header HMAC: {@code Content-Hmac} no formato {@code sha256=<hex>}; alias
 *       {@code X-Webhook-Signature} aceito sem prefixo.
 *   <li>Idempotency-Key: Clicksign nao envia. Quando ausente, derivamos via
 *       {@code SHA-256(provider + ":" + payload)} — estavel pra mesmo payload, permite dedup no
 *       outbox compartilhado da Sprint 4.
 *   <li>idEventoExterno: SHA-256 do payload — unique key em (envelope_id, id_evento_externo)
 *       garante idempotencia mesmo se controlador chamar use case mais de uma vez por race.
 * </ul>
 */
@RestController
@Tag(name = "webhooks", description = "Webhook de assinatura digital (HMAC + idempotencia + outbox)")
public class AssinaturaWebhookController {

    static final String CODIGO_HEADER_OBRIGATORIO = "ASN-400-001";
    static final String CODIGO_PROVIDER_NAO_SUPORTADO = "ASN-400-002";
    static final String CODIGO_PAYLOAD_TAMANHO = "ASN-400-003";
    private static final String HMAC_PREFIXO = "sha256=";
    private static final Set<String> PROVIDERS_SUPORTADOS = Set.of("clicksign");
    // Webhook Clicksign tipico < 10KB; 64KB cobre payloads com metadata + selo + assinaturas
    // multiplas. Defensivo contra DoS via body gigante (Spring default ~1MB sem limite explicito
    // por endpoint).
    private static final int PAYLOAD_MAX_BYTES = 64 * 1024;

    private final WebhookSignatureValidator signatureValidator;
    private final ProcessarWebhookAssinaturaUseCase processarUseCase;
    private final ObjectMapper objectMapper;

    public AssinaturaWebhookController(
            WebhookSignatureValidator signatureValidator,
            ProcessarWebhookAssinaturaUseCase processarUseCase,
            ObjectMapper objectMapper) {
        this.signatureValidator = signatureValidator;
        this.processarUseCase = processarUseCase;
        this.objectMapper = objectMapper;
    }

    @PostMapping(path = "/api/v1/webhooks/assinatura/{provider}", consumes = "application/json")
    @Operation(
            summary = "Receber callback de assinatura digital",
            description = "Valida HMAC SHA-256 do payload (header Content-Hmac formato sha256=<hex> ou "
                    + "X-Webhook-Signature em hex puro), grava o evento no outbox compartilhado da Sprint 4 e "
                    + "roteia por event.name. Idempotente: Idempotency-Key ausente derivado do payload via "
                    + "SHA-256.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "202",
                description =
                        "Callback aceito (novo ou duplicado idempotente; status final processado quando aplicavel)"),
        @ApiResponse(
                responseCode = "400",
                description = "Headers obrigatorios ausentes, body invalido ou provider nao suportado",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "401",
                description = "Assinatura HMAC invalida",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<Void> receber(
            @PathVariable("provider") String provider,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "Content-Hmac", required = false) String contentHmac,
            @RequestHeader(value = "X-Webhook-Signature", required = false) String signaturePadrao,
            @RequestBody String payload) {

        if (provider == null || provider.isBlank()) {
            throw new ValidacaoException(CODIGO_HEADER_OBRIGATORIO, "Path param {provider} eh obrigatorio");
        }
        String providerNormalizado = provider.toLowerCase();
        if (!PROVIDERS_SUPORTADOS.contains(providerNormalizado)) {
            throw new ValidacaoException(
                    CODIGO_PROVIDER_NAO_SUPORTADO, "Provider de assinatura nao suportado: " + provider);
        }

        if (payload == null || payload.isBlank()) {
            throw new ValidacaoException(CODIGO_HEADER_OBRIGATORIO, "Body do webhook e obrigatorio");
        }
        if (payload.length() > PAYLOAD_MAX_BYTES) {
            throw new ValidacaoException(
                    CODIGO_PAYLOAD_TAMANHO, "Body do webhook excede tamanho maximo de " + PAYLOAD_MAX_BYTES + " bytes");
        }

        String signature = extrairSignature(contentHmac, signaturePadrao);
        if (signature == null) {
            throw new ValidacaoException(
                    CODIGO_HEADER_OBRIGATORIO, "Header Content-Hmac (ou X-Webhook-Signature) e obrigatorio");
        }

        String secretKey = providerNormalizado + "-assinatura";
        if (!signatureValidator.isValid(secretKey, payload, signature)) {
            throw new BadCredentialsException("Assinatura de webhook invalida");
        }

        String payloadHash = sha256Hex(payload);
        String idempotency = idempotencyKey != null && !idempotencyKey.isBlank()
                ? idempotencyKey
                : providerNormalizado + ":" + payloadHash;

        AssinaturaCallbackClicksign body = parsearPayload(payload);
        processarUseCase.executar(providerNormalizado, idempotency, payloadHash, signature, payload, body);

        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    /**
     * Extrai assinatura HMAC normalizada (hex puro) dos headers aceitos. {@code Content-Hmac}
     * Clicksign vem no formato {@code sha256=<hex>}; {@code X-Webhook-Signature} eh hex puro
     * (alias generico).
     */
    private static String extrairSignature(String contentHmac, String signaturePadrao) {
        if (contentHmac != null && !contentHmac.isBlank()) {
            String trimmed = contentHmac.trim();
            return trimmed.toLowerCase().startsWith(HMAC_PREFIXO) ? trimmed.substring(HMAC_PREFIXO.length()) : trimmed;
        }
        if (signaturePadrao != null && !signaturePadrao.isBlank()) {
            return signaturePadrao.trim();
        }
        return null;
    }

    private AssinaturaCallbackClicksign parsearPayload(String payload) {
        try {
            return objectMapper.readValue(payload, AssinaturaCallbackClicksign.class);
        } catch (JsonProcessingException ex) {
            throw new ValidacaoException(CODIGO_HEADER_OBRIGATORIO, "Body do webhook nao e JSON valido");
        }
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 indisponivel no runtime", ex);
        }
    }
}
