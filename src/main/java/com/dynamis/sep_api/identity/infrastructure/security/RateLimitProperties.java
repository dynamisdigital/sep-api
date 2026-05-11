package com.dynamis.sep_api.identity.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Limites por IP em endpoints sensiveis de autenticacao (Sprint 5 Task 5.4). Aplicado pelo {@link
 * RateLimitFilter} via Resilience4j {@code RateLimiterRegistry}.
 */
@Component
@ConfigurationProperties(prefix = "app.security.rate-limit")
public class RateLimitProperties {

    private int loginPerMinutePerIp = 5;
    private int totpVerifyPerMinutePerIp = 5;

    public int getLoginPerMinutePerIp() {
        return loginPerMinutePerIp;
    }

    public void setLoginPerMinutePerIp(int loginPerMinutePerIp) {
        this.loginPerMinutePerIp = loginPerMinutePerIp;
    }

    public int getTotpVerifyPerMinutePerIp() {
        return totpVerifyPerMinutePerIp;
    }

    public void setTotpVerifyPerMinutePerIp(int totpVerifyPerMinutePerIp) {
        this.totpVerifyPerMinutePerIp = totpVerifyPerMinutePerIp;
    }
}
