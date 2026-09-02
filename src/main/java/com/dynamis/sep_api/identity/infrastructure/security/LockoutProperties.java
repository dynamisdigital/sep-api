package com.dynamis.sep_api.identity.infrastructure.security;

import com.dynamis.sep_api.identity.domain.model.PoliticaLockout;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Politica de lockout por conta (Sprint 5 Task 5.4). Falhas em {@code windowMinutes} acumulam ate
 * {@code maxAttempts}; ao atingir o limite, a conta fica bloqueada por {@code lockoutMinutes}.
 *
 * <p>Os tres campos exigem valor estritamente positivo (Sprint 35 Task 35.1), <b>por dois motivos
 * diferentes</b>:
 *
 * <ul>
 *   <li><b>{@code maxAttempts}</b> ja era invariante de dominio: o construtor compacto de
 *       {@link PoliticaLockout} rejeita {@code < 1}. Como {@code LockoutService.politica()} monta o
 *       record em <b>todo</b> login, um {@code 0} virava {@code IllegalArgumentException} a cada
 *       requisicao — 500 barulhento. A validacao aqui troca isso por falha de boot, que e melhor,
 *       mas nao cobre um buraco: cobre um erro que ja aparecia.
 *   <li><b>{@code windowMinutes} e {@code lockoutMinutes}</b> sao o caso silencioso, e o motivo
 *       <b>nao esta neste repo</b>. O dominio aceita duracao <b>zero</b> ({@code PoliticaLockout}
 *       so rejeita negativo), e zero desliga o lockout sem nenhum sinal: janela zero so detecta
 *       falhas simultaneas, bloqueio zero expira no instante em que nasce. Pior, a politica e
 *       publicada por {@code GET /api/v1/auth/politica-lockout} (Sprint 34) e consumida pelo
 *       {@code sep-app} em {@code sep-app/src/app/core/auth/politica-lockout.service.ts}, onde
 *       {@code ehUtilizavel} exige os <b>tres</b> como inteiros positivos e {@code consultar()}
 *       devolve {@code null} se qualquer um falhar. O tratamento la e tudo-ou-nada: um unico
 *       {@code APP_LOCKOUT_WINDOW_MINUTES=0} ({@code application.yml}, bloco
 *       {@code app.security.lockout}) apaga os tres numeros da tela {@code /account-locked} de uma
 *       vez — sem erro, sem log, so a copy generica. Relaxar qualquer um destes dois limites
 *       reabre esse caminho.
 * </ul>
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

    @Min(value = 1, message = "zero ou negativo faz PoliticaLockout lancar em todo login")
    private int maxAttempts = DEFAULT_MAX_ATTEMPTS;

    @Min(value = 1, message = "janela zero so detecta falhas simultaneas; deixa de bloquear")
    private int windowMinutes = 15;

    @Min(value = 1, message = "bloqueio zero expira ao nascer e zera a tela /account-locked")
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
