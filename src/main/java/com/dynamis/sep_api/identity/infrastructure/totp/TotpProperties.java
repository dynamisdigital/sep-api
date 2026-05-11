package com.dynamis.sep_api.identity.infrastructure.totp;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Propriedades de configuracao do TOTP (Sprint 5 Task 5.2). Valores reais vem de variaveis de
 * ambiente; valores default servem apenas para dev-local.
 */
@Component
@ConfigurationProperties(prefix = "app.security.totp")
public class TotpProperties {

    /** Issuer exibido no app autenticador (parte da URI {@code otpauth://}). */
    private String issuer = "SEP";

    /**
     * Janelas de tolerancia para clock skew. Cada unidade representa 30s para a frente e para tras
     * (ex.: window-size=1 aceita codigo do periodo anterior, atual e seguinte).
     */
    private int windowSize = 1;

    /** Chave usada para cifrar o secret TOTP em repouso. Minimo 32 bytes; nunca usar default em prod. */
    private String encryptionKey;

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public int getWindowSize() {
        return windowSize;
    }

    public void setWindowSize(int windowSize) {
        this.windowSize = windowSize;
    }

    public String getEncryptionKey() {
        return encryptionKey;
    }

    public void setEncryptionKey(String encryptionKey) {
        this.encryptionKey = encryptionKey;
    }
}
