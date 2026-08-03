package com.dynamis.sep_api.identity.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Limites por IP em endpoints sensiveis de autenticacao (Sprint 5 Task 5.4). Aplicado pelo {@link
 * RateLimitFilter} via Resilience4j.
 *
 * <p>Defaults sao <b>10</b> para respeitar a invariante {@code rate-limit > lockout.max-attempts}
 * (default 5) sem depender do {@code application.yml} — ate a Sprint 34 valiam 5, iguais ao limiar
 * de lockout, e um contexto que nao carregasse o YAML nascia com o {@code 429} mascarando o
 * {@code 423}. O {@link RateLimitLockoutValidator} trava a invariante no boot.
 */
@Component
@ConfigurationProperties(prefix = "app.security.rate-limit")
public class RateLimitProperties {

    static final int DEFAULT_POR_MINUTO_POR_IP = 10;

    private int loginPerMinutePerIp = DEFAULT_POR_MINUTO_POR_IP;
    private int totpVerifyPerMinutePerIp = DEFAULT_POR_MINUTO_POR_IP;

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
