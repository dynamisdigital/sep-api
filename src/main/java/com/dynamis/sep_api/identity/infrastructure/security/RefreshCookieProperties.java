package com.dynamis.sep_api.identity.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Propriedades do cookie HttpOnly de refresh token (follow-up 5F-FIX-02 da Sprint 5).
 *
 * <p>Mapeadas em {@code app.refresh-cookie.*} (ver {@code application.yml}). Em producao,
 * {@code secure=true} e {@code same-site=Strict} sao obrigatorios; em dev-local, {@code Lax}
 * + {@code secure=false} permitem teste com {@code http://localhost}.
 */
@Component
@ConfigurationProperties(prefix = "app.refresh-cookie")
public class RefreshCookieProperties {

    /** Nome do cookie ({@code Set-Cookie}). */
    private String name = "sep-refresh";

    /** Path do cookie. Mantenha sob {@code /api/v1/auth} para evitar envio em outras rotas. */
    private String path = "/api/v1/auth";

    /** Flag {@code Secure} (exige HTTPS). Default {@code false} pra dev; override em prod. */
    private boolean secure = false;

    /** Atributo {@code SameSite}: {@code Strict}, {@code Lax} ou {@code None}. */
    private String sameSite = "Lax";

    /**
     * Domain explicito (opcional). Quando vazio, o navegador limita ao host atual. Usar apenas em
     * ambientes com subdominios consolidados (ex.: {@code .sep.com.br}).
     */
    private String domain = "";

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public boolean isSecure() {
        return secure;
    }

    public void setSecure(boolean secure) {
        this.secure = secure;
    }

    public String getSameSite() {
        return sameSite;
    }

    public void setSameSite(String sameSite) {
        this.sameSite = sameSite;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }
}
