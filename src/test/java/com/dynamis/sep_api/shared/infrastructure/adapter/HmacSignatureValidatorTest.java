package com.dynamis.sep_api.shared.infrastructure.adapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.assertj.core.api.Assertions.assertThat;

class HmacSignatureValidatorTest {

    private static final String SECRET = "dev-webhook-secret-change-me";
    private static final String PROVIDER = "celcoin";

    private HmacSignatureValidator validator;

    @BeforeEach
    void setUp() {
        validator = new HmacSignatureValidator();
        validator.setSecrets(Map.of(PROVIDER, SECRET));
    }

    @Test
    void hmacValidoRetornaTrue() throws Exception {
        String payload = "{\"id\":\"abc\"}";
        String signature = computeHmac(SECRET, payload);

        assertThat(validator.isValid(PROVIDER, payload, signature)).isTrue();
    }

    @Test
    void hmacInvalidoRetornaFalse() {
        assertThat(validator.isValid(PROVIDER, "{\"id\":\"abc\"}", "00ff")).isFalse();
    }

    @Test
    void providerSemSecretRetornaFalse() throws Exception {
        String payload = "{}";
        String signature = computeHmac(SECRET, payload);

        assertThat(validator.isValid("desconhecido", payload, signature)).isFalse();
    }

    @Test
    void signatureNaoHexRetornaFalse() {
        assertThat(validator.isValid(PROVIDER, "{}", "not-hex-zzz")).isFalse();
    }

    @Test
    void argumentosNullRetornamFalse() {
        assertThat(validator.isValid(null, "{}", "00")).isFalse();
        assertThat(validator.isValid(PROVIDER, null, "00")).isFalse();
        assertThat(validator.isValid(PROVIDER, "{}", null)).isFalse();
    }

    private static String computeHmac(String secret, String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }
}
