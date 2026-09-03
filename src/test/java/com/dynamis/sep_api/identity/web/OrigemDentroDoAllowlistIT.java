package com.dynamis.sep_api.identity.web;

import com.dynamis.sep_api.identity.domain.model.LoginAttempt;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lado <b>de dentro</b> do allowlist de proxy (Sprint 35 Task 35.2). Declara o loopback como proxy
 * confiavel, que e o papel do balanceador na Fase 5, e ai o {@code X-Forwarded-For} <b>tem</b> de ser
 * respeitado — senao a troca de estrategia teria custo sem beneficio: todo mundo atras do
 * balanceador compartilharia um unico IP e o rate limit por IP viraria global.
 *
 * <p><b>Limite honesto deste teste</b>: ele passa igual na configuracao vulneravel de antes da
 * Sprint 35, porque tanto o {@code ForwardedHeaderFilter} quanto o {@code RemoteIpValve} com
 * loopback confiavel honram o header. Ele nao prova nada sobre o bypass — quem faz isso e o
 * {@link OrigemForaDoAllowlistIT}. O que ele guarda e a correcao <b>estrita demais</b>: um
 * {@code extrairIp} que ignorasse o header em qualquer situacao passaria la e reprovaria aqui.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class OrigemDentroDoAllowlistIT extends OrigemDaRequestITBase {

    /** Ver o javadoc do override irmao em {@code OrigemForaDoAllowlistIT} sobre o teto de rate limit. */
    @DynamicPropertySource
    static void configurar(DynamicPropertyRegistry registry) {
        registry.add("server.tomcat.remoteip.internal-proxies", () -> "127\\.0\\.0\\.1|0:0:0:0:0:0:0:1");
        registry.add("app.security.rate-limit.login-per-minute-per-ip", () -> 1000);
    }

    @Test
    void xForwardedForDeProxyConfiavelEhRespeitado() {
        tentarLogin("origem-dentro@sep.test");

        assertThat(tentativasRegistradas())
                .singleElement()
                .extracting(LoginAttempt::getIp)
                .as("vindo de proxy confiavel o header carrega a origem real do cliente")
                .isEqualTo(ORIGEM_NO_HEADER);
    }
}
