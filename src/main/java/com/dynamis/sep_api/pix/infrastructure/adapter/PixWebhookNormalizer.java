package com.dynamis.sep_api.pix.infrastructure.adapter;

import com.dynamis.sep_api.pix.application.port.out.dto.EventoWebhookPixNormalizado;
import com.dynamis.sep_api.pix.domain.vo.TipoPixWebhookEvent;
import com.dynamis.sep_api.pix.infrastructure.adapter.celcoin.dto.CelcoinPixWebhookPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Normaliza o payload bruto do webhook Pix (formato Celcoin) para a linguagem de dominio SEP e
 * calcula o SHA-256 hex do corpo. Componente compartilhado pelos adapters fake e Celcoin para
 * evitar duplicacao do parsing/hash — o formato do envelope e o mesmo, o que muda e a origem
 * (HTTP real vs deterministico).
 *
 * <p>Minimizacao de dados: o use case de processamento so ve o {@link EventoWebhookPixNormalizado}
 * (com hash), nunca o JSON original.
 */
@Component
public class PixWebhookNormalizer {

    private final ObjectMapper objectMapper;

    public PixWebhookNormalizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public EventoWebhookPixNormalizado normalizar(String payloadBruto) {
        if (payloadBruto == null || payloadBruto.isBlank()) {
            throw new IllegalArgumentException("payload do webhook Pix vazio");
        }
        String payloadHash = sha256Hex(payloadBruto);
        CelcoinPixWebhookPayload payload = parse(payloadBruto);
        return new EventoWebhookPixNormalizado(
                classificar(payload.eventType()),
                payload.eventId(),
                payload.endToEndId(),
                payload.valor(),
                payload.transferId(),
                payloadHash);
    }

    private CelcoinPixWebhookPayload parse(String payloadBruto) {
        try {
            return objectMapper.readValue(payloadBruto, CelcoinPixWebhookPayload.class);
        } catch (Exception ex) {
            throw new IllegalArgumentException("payload do webhook Pix invalido", ex);
        }
    }

    private TipoPixWebhookEvent classificar(String eventType) {
        if (eventType == null) {
            return TipoPixWebhookEvent.DESCONHECIDO;
        }
        return switch (eventType) {
            case "pix.received", "cashin.completed" -> TipoPixWebhookEvent.RECEBIMENTO_PIX;
            case "pix.transfer.status", "cashout.status" -> TipoPixWebhookEvent.STATUS_TRANSFERENCIA;
            default -> TipoPixWebhookEvent.DESCONHECIDO;
        };
    }

    private static String sha256Hex(String valor) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 indisponivel na JVM", ex);
        }
    }
}
