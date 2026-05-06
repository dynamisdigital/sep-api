package com.dynamis.sep_api.shared.infrastructure.adapter;

import com.dynamis.sep_api.shared.application.port.out.WebhookSignatureValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Adapter que valida a assinatura HMAC-SHA256 hex de um payload de webhook contra o secret
 * configurado por provider. Comparacao usa {@link MessageDigest#isEqual(byte[], byte[])} para
 * mitigar ataques de timing.
 *
 * <p>Secrets sao lidos da propriedade {@code app.webhooks.secrets.<provider>}, ex.:
 * {@code app.webhooks.secrets.celcoin}.
 */
@Component
@ConfigurationProperties(prefix = "app.webhooks")
public class HmacSignatureValidator implements WebhookSignatureValidator {

    private static final Logger log = LoggerFactory.getLogger(HmacSignatureValidator.class);
    private static final String ALGORITHM = "HmacSHA256";

    private Map<String, String> secrets = Map.of();

    public Map<String, String> getSecrets() {
        return secrets;
    }

    public void setSecrets(Map<String, String> secrets) {
        this.secrets = secrets == null ? Map.of() : Map.copyOf(secrets);
    }

    @Override
    public boolean isValid(String provider, String payload, String signature) {
        if (provider == null || payload == null || signature == null) {
            return false;
        }
        String secret = secrets.get(provider);
        if (secret == null || secret.isBlank()) {
            log.warn("Webhook recebido para provider sem secret configurado: {}", provider);
            return false;
        }
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            byte[] computed = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            byte[] received;
            try {
                received = HexFormat.of().parseHex(signature.trim().toLowerCase());
            } catch (IllegalArgumentException ex) {
                return false;
            }
            return MessageDigest.isEqual(computed, received);
        } catch (Exception ex) {
            log.error("Falha ao calcular HMAC para provider {}", provider, ex);
            return false;
        }
    }
}
