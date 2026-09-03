package com.dynamis.sep_api.identity.web;

import com.dynamis.sep_api.identity.domain.model.LoginAttempt;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lado <b>de fora</b> do allowlist de proxy (Sprint 35 Task 35.2). Este IT carrega a prova de
 * seguranca da Task: com o {@code internal-proxies} que o {@code application.yml} entrega — vazio,
 * isto e, nenhuma origem confiavel —, o {@code X-Forwarded-For} que o cliente manda tem de ser
 * <b>ignorado</b> e a origem registrada tem de ser o peer real da conexao.
 *
 * <p><b>Nao fixa o allowlist de proposito.</b> Herda-lo do {@code application.yml} e o que mantem
 * viva a mutacao do Step 035.2.2 — remover {@code internal-proxies} mantendo {@code native} devolve
 * o default do Spring Boot, que confia em toda faixa privada, inclusive {@code 127.0.0.1}, e este
 * teste volta a ver o valor forjado. A primeira assercao existe para que a premissa seja verificada
 * em vez de suposta: um {@code APP_TRUSTED_PROXIES} exportado no ambiente mudaria o significado do
 * teste sem nenhum sinal.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class OrigemForaDoAllowlistIT extends OrigemDaRequestITBase {

    /**
     * O teto de rate limit nao e folga: o {@code @DynamicPropertySource} participa da chave de cache
     * do contexto, e e este override que garante a este IT um {@code RateLimitFilter} proprio, com o
     * mapa de limitadores limpo. Remove-lo por parecer inutil reintroduz contaminacao entre ITs.
     */
    @DynamicPropertySource
    static void configurar(DynamicPropertyRegistry registry) {
        registry.add("app.security.rate-limit.login-per-minute-per-ip", () -> 1000);
    }

    @Test
    void xForwardedForDeOrigemNaoConfiavelEhIgnorado() {
        assertThat(allowlistVigente())
                .as("o teste so vale com o allowlist que o application.yml entrega; APP_TRUSTED_PROXIES"
                        + " no ambiente invalidaria a premissa")
                .isNullOrEmpty();

        tentarLogin("origem-fora@sep.test");

        assertThat(tentativasRegistradas())
                .singleElement()
                .extracting(LoginAttempt::getIp)
                .as("com allowlist vazio o header e do cliente, nao de um proxy: nao pode virar a origem")
                .isNotEqualTo(ORIGEM_NO_HEADER)
                .isIn("127.0.0.1", "0:0:0:0:0:0:0:1");
    }
}
