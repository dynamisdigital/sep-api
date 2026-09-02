package com.dynamis.sep_api.identity.infrastructure.security;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Politica de lockout por conta (Sprint 5 Task 5.4). Falhas em {@code windowMinutes} acumulam ate
 * {@code maxAttempts}; ao atingir o limite, a conta fica bloqueada por {@code lockoutMinutes}.
 *
 * <p><b>Os tres campos sao validados como estritamente positivos (Sprint 35 Task 35.1), e o motivo
 * nao esta neste repo.</b> A politica e publicada por {@code GET /api/v1/auth/politica-lockout}
 * (Sprint 34) e consumida pelo {@code sep-app} em {@code core/auth/politica-lockout.service.ts},
 * cujo {@code ehUtilizavel} exige os <b>tres</b> como inteiros positivos e devolve {@code null} se
 * qualquer um falhar. O tratamento la e tudo-ou-nada: um unico
 * {@code APP_SECURITY_LOCKOUT_WINDOW_MINUTES=0} derruba os tres numeros da tela {@code /account-locked} de
 * uma vez, e a jornada de conta bloqueada degrada em silencio — sem erro, sem log, so a tela
 * generica. Relaxar qualquer um destes limites reabre esse caminho.
 *
 * <p>A validacao e declarativa porque {@code @Validated} roda <b>depois</b> do bind, e portanto ja
 * enxerga o relaxed binding do {@code @ConfigurationProperties}. Isto e diferente do
 * {@link RateLimitLockoutValidator}, que le as properties por conta propria e por isso precisa do
 * {@code Binder} (ver o javadoc de {@code validar}); aqui esse problema nao existe.
 */
@Component
@ConfigurationProperties(prefix = "app.security.lockout")
@Validated
public class LockoutProperties {

    static final int DEFAULT_MAX_ATTEMPTS = 5;

    @Min(1)
    private int maxAttempts = DEFAULT_MAX_ATTEMPTS;

    @Min(1)
    private int windowMinutes = 15;

    @Min(1)
    private int lockoutMinutes = 30;

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public int getWindowMinutes() {
        return windowMinutes;
    }

    public void setWindowMinutes(int windowMinutes) {
        this.windowMinutes = windowMinutes;
    }

    public int getLockoutMinutes() {
        return lockoutMinutes;
    }

    public void setLockoutMinutes(int lockoutMinutes) {
        this.lockoutMinutes = lockoutMinutes;
    }
}
