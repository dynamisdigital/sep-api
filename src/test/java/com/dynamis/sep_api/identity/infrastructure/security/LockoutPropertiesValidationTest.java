package com.dynamis.sep_api.identity.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validacao de {@link LockoutProperties} no boot (Sprint 35 Task 35.1). Cada campo zerado precisa
 * derrubar o contexto: o {@code sep-app} trata a politica como tudo-ou-nada
 * ({@code politica-lockout.service.ts}, {@code ehUtilizavel}), entao um unico campo invalido apaga
 * os tres numeros da tela {@code /account-locked} sem nenhum sinal de erro.
 *
 * <p>Usa {@link ApplicationContextRunner} — mesmo desenho do {@code ProviderWiringTest} — porque a
 * afirmacao e sobre o <b>bind</b>, e nao sobre o POJO: instanciar a classe e chamar o setter nao
 * dispara validacao nenhuma e provaria coisa alguma.
 */
class LockoutPropertiesValidationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Config.class);

    @Test
    void defaults_bootPassa() {
        runner.run(contexto -> {
            LockoutProperties propriedades = contexto.getBean(LockoutProperties.class);
            assertThat(propriedades.getMaxAttempts()).isEqualTo(LockoutProperties.DEFAULT_MAX_ATTEMPTS);
            assertThat(propriedades.getWindowMinutes()).isEqualTo(15);
            assertThat(propriedades.getLockoutMinutes()).isEqualTo(30);
        });
    }

    @Test
    void valoresPositivosExternos_bootPassa() {
        runner.withPropertyValues(
                        "app.security.lockout.max-attempts=3",
                        "app.security.lockout.window-minutes=10",
                        "app.security.lockout.lockout-minutes=20")
                .run(contexto -> {
                    LockoutProperties propriedades = contexto.getBean(LockoutProperties.class);
                    assertThat(propriedades.getMaxAttempts()).isEqualTo(3);
                    assertThat(propriedades.getWindowMinutes()).isEqualTo(10);
                    assertThat(propriedades.getLockoutMinutes()).isEqualTo(20);
                });
    }

    @Test
    void maxAttemptsZero_derrubaOBoot() {
        runner.withPropertyValues("app.security.lockout.max-attempts=0").run(contexto -> assertThat(contexto)
                .getFailure()
                .rootCause()
                .isInstanceOf(BindValidationException.class)
                .hasMessageContaining("maxAttempts"));
    }

    @Test
    void windowMinutesZero_derrubaOBoot() {
        runner.withPropertyValues("app.security.lockout.window-minutes=0").run(contexto -> assertThat(contexto)
                .getFailure()
                .rootCause()
                .isInstanceOf(BindValidationException.class)
                .hasMessageContaining("windowMinutes"));
    }

    @Test
    void lockoutMinutesZero_derrubaOBoot() {
        runner.withPropertyValues("app.security.lockout.lockout-minutes=0").run(contexto -> assertThat(contexto)
                .getFailure()
                .rootCause()
                .isInstanceOf(BindValidationException.class)
                .hasMessageContaining("lockoutMinutes"));
    }

    @Test
    void valorNegativo_derrubaOBoot() {
        runner.withPropertyValues("app.security.lockout.max-attempts=-1").run(contexto -> assertThat(contexto)
                .getFailure()
                .rootCause()
                .isInstanceOf(BindValidationException.class)
                .hasMessageContaining("maxAttempts"));
    }

    /**
     * O criterio que decidiu o mecanismo (Step 035.1.1): a validacao declarativa roda depois do
     * bind, entao enxerga as formas que o relaxed binding aceita. A forma camelCase e o nome do
     * campo no POJO, a que da vontade de escrever num {@code application-prod.yml}. Se a validacao
     * casasse chave exata, esse override passaria despercebido e o boot subiria com politica
     * invalida.
     *
     * <p>O knob de deploy <b>nao</b> e este: o {@code application.yml} resolve
     * {@code APP_LOCKOUT_WINDOW_MINUTES} para a chave kebab-case, ja coberta por
     * {@link #windowMinutesZero_derrubaOBoot()}. O que estes dois testes cobrem e a outra porta —
     * a chave canonica, que o relaxed binding aceita sem passar pelo {@code ${...}}.
     */
    @Test
    void enxergaOverrideEmCamelCase() {
        runner.withPropertyValues("app.security.lockout.windowMinutes=0").run(contexto -> assertThat(contexto)
                .getFailure()
                .rootCause()
                .isInstanceOf(BindValidationException.class)
                .hasMessageContaining("windowMinutes"));
    }

    /**
     * Precisa de {@link SystemEnvironmentPropertySource} de verdade: e a <b>classe</b> que resolve
     * {@code MAIUSCULA_COM_UNDERSCORE}, nao o Boot. Com um {@code MapPropertySource} de mesma chave
     * o teste fica verde provando nada — foi o que aconteceu na primeira versao. O nome canonico
     * faz o {@code addFirst} substituir o {@code systemEnvironment} real do contexto em vez de
     * empilhar sobre ele, o que aqui e o isolamento desejado.
     */
    @Test
    void enxergaOverrideEmFormatoDeVariavelDeAmbiente() {
        runner.withInitializer(contexto -> contexto.getEnvironment()
                        .getPropertySources()
                        .addFirst(new SystemEnvironmentPropertySource(
                                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                                Map.of("APP_SECURITY_LOCKOUT_WINDOW_MINUTES", "0"))))
                .run(contexto -> assertThat(contexto)
                        .getFailure()
                        .rootCause()
                        .isInstanceOf(BindValidationException.class)
                        .hasMessageContaining("windowMinutes"));
    }

    @EnableConfigurationProperties(LockoutProperties.class)
    static class Config {}
}
