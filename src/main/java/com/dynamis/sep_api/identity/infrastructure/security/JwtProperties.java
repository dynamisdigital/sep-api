package com.dynamis.sep_api.identity.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Propriedades do par access + refresh token (Sprint 5 Task 5.3). Access token continua emitido
 * pelo {@link JwtTokenProvider}; refresh token vive em banco como hash SHA-256.
 */
@Component
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    private long accessExpirationSeconds = 900L;
    private long refreshExpirationSeconds = 2_592_000L;

    public long getAccessExpirationSeconds() {
        return accessExpirationSeconds;
    }

    public void setAccessExpirationSeconds(long accessExpirationSeconds) {
        this.accessExpirationSeconds = accessExpirationSeconds;
    }

    public long getRefreshExpirationSeconds() {
        return refreshExpirationSeconds;
    }

    public void setRefreshExpirationSeconds(long refreshExpirationSeconds) {
        this.refreshExpirationSeconds = refreshExpirationSeconds;
    }
}
