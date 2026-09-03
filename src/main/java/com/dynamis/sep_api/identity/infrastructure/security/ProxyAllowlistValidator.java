package com.dynamis.sep_api.identity.infrastructure.security;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.util.StringUtils;

/**
 * Derruba o boot em {@code prod} quando o allowlist de proxy esta vazio (Sprint 35 Task 35.2).
 *
 * <p>O default de {@code server.tomcat.remoteip.internal-proxies} e vazio de proposito — nao confiar
 * em ninguem e a postura correta enquanto nao ha balanceador. O problema e o dia em que houver: com
 * {@code forward-headers-strategy: native} e allowlist vazio, o app <b>sobe normalmente</b> atras do
 * balanceador e degrada em silencio, e as tres consequencias sao invisiveis em teste:
 *
 * <ul>
 *   <li>o rate limit por IP colapsa no IP do balanceador — vira global, e um cliente derruba todos;
 *   <li>{@code X-Forwarded-Proto} deixa de ser lido, {@code isSecure()} fica falso e o Spring
 *       Security para de emitir HSTS;
 *   <li>o springdoc anuncia a origem interna em {@code /v3/api-docs}, o que tambem desalinha o
 *       {@code contract:check} do {@code sep-app}.
 * </ul>
 *
 * <p>Nenhuma das tres da erro. Por isso a checagem e no boot, e nao um alerta: e o mesmo raciocinio
 * do {@link RateLimitLockoutValidator} — configuracao que desliga um controle em silencio deve
 * impedir a subida, nao ser descoberta em producao.
 *
 * <p>Restrito a {@code prod} porque em dev e teste nao ha proxy: exigir allowlist ali obrigaria todo
 * mundo a inventar um valor, e valor inventado e como allowlist largo demais nasce.
 */
@Configuration
@Profile("prod")
public class ProxyAllowlistValidator {

    static final String PROPERTY = "server.tomcat.remoteip.internal-proxies";

    private final ServerProperties serverProperties;

    public ProxyAllowlistValidator(ServerProperties serverProperties) {
        this.serverProperties = serverProperties;
    }

    @PostConstruct
    void validar() {
        validar(serverProperties.getTomcat().getRemoteip().getInternalProxies());
    }

    static void validar(String internalProxies) {
        if (!StringUtils.hasText(internalProxies)) {
            throw new IllegalStateException(PROPERTY + " vazio em prod (defina APP_TRUSTED_PROXIES): o"
                    + " X-Forwarded-For do balanceador seria ignorado, o rate limit por IP colapsaria no IP"
                    + " dele, o HSTS deixaria de ser emitido e o OpenAPI anunciaria a origem interna — tudo"
                    + " sem nenhum erro visivel. O valor e regex Java, nao CIDR.");
        }
    }
}
