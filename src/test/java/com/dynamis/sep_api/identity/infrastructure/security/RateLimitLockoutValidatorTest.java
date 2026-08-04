package com.dynamis.sep_api.identity.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.env.MockPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Invariante {@code rate-limit > lockout.max-attempts} (Sprint 34 Task 34.4). Roda sem contexto
 * Spring porque o validador delega para um metodo estatico — mesmo desenho do
 * {@code ProviderFlagsValidatorTest}.
 */
class RateLimitLockoutValidatorTest {

    @Test
    void semPropriedades_defaultsDosPojosPassam() {
        assertThatCode(() -> RateLimitLockoutValidator.validar(new MockEnvironment()))
                .doesNotThrowAnyException();
    }

    @Test
    void valoresExternosValidos_passam() {
        MockEnvironment env = new MockEnvironment()
                .withProperty(RateLimitLockoutValidator.MAX_ATTEMPTS, "3")
                .withProperty(RateLimitLockoutValidator.LOGIN_POR_IP, "4")
                .withProperty(RateLimitLockoutValidator.TOTP_VERIFY_POR_IP, "4");

        assertThatCode(() -> RateLimitLockoutValidator.validar(env)).doesNotThrowAnyException();
    }

    /**
     * O caso que existia de verdade ate a Sprint 33: com os dois em 5 o {@code RateLimitFilter}
     * barra com {@code 429} justamente a tentativa que responderia {@code 423}.
     */
    @Test
    void limiteIgualAoLimiarDeLockout_falhaCitandoPropertiesEValores() {
        MockEnvironment env = new MockEnvironment()
                .withProperty(RateLimitLockoutValidator.MAX_ATTEMPTS, "5")
                .withProperty(RateLimitLockoutValidator.LOGIN_POR_IP, "5");

        assertThatThrownBy(() -> RateLimitLockoutValidator.validar(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(RateLimitLockoutValidator.LOGIN_POR_IP)
                .hasMessageContaining(RateLimitLockoutValidator.MAX_ATTEMPTS)
                .hasMessageContaining("5");
    }

    /** O limite do TOTP verify tambem conta: {@code VerificarTotpUseCase} chama o mesmo lockout. */
    @Test
    void limiteDoTotpVerifyAbaixoDoLimiar_falha() {
        MockEnvironment env = new MockEnvironment()
                .withProperty(RateLimitLockoutValidator.MAX_ATTEMPTS, "5")
                .withProperty(RateLimitLockoutValidator.LOGIN_POR_IP, "10")
                .withProperty(RateLimitLockoutValidator.TOTP_VERIFY_POR_IP, "2");

        assertThatThrownBy(() -> RateLimitLockoutValidator.validar(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(RateLimitLockoutValidator.TOTP_VERIFY_POR_IP);
    }

    /**
     * O relaxed binding do {@code @ConfigurationProperties} aceita a forma camelCase — que e o nome
     * do campo no POJO, e portanto a que da vontade de escrever num {@code application-prod.yml}.
     * Com {@code environment.getProperty}, que casa a chave exata, o validador nao enxergava esse
     * override e aprovava o valor canonico do {@code application.yml} enquanto o bind montava
     * outro: validava um mundo que nao ia existir.
     */
    @Test
    void enxergaOverrideEmCamelCase() {
        MockEnvironment env = new MockEnvironment()
                .withProperty(RateLimitLockoutValidator.MAX_ATTEMPTS, "5")
                .withProperty("app.security.rate-limit.loginPerMinutePerIp", "3");

        assertThatThrownBy(() -> RateLimitLockoutValidator.validar(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("3");
    }

    /** Mesma armadilha pela outra forma que o relaxed binding aceita. */
    @Test
    void enxergaOverrideComUnderscore() {
        MockEnvironment env = new MockEnvironment()
                .withProperty(RateLimitLockoutValidator.MAX_ATTEMPTS, "5")
                .withProperty("app.security.rate_limit.login_per_minute_per_ip", "2");

        assertThatThrownBy(() -> RateLimitLockoutValidator.validar(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("2");
    }

    /**
     * A validacao so vale se estiver ligada, e os demais casos chamam o metodo estatico direto —
     * apagar o {@code @Component} deixaria todos verdes com a guarda de boot morta. O
     * {@code contextoNaoSobeComAInvarianteQuebrada} abaixo tambem nao pega, porque
     * {@code register(Class)} ignora a anotacao. Aqui exercita-se a descoberta de verdade, sem
     * instanciar nada: os outros componentes deste pacote precisariam de repositorios para subir.
     */
    @Test
    void eDescobertoPeloComponentScan() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(true);

        assertThat(scanner.findCandidateComponents(RateLimitLockoutValidator.class.getPackageName()))
                .extracting(BeanDefinition::getBeanClassName)
                .contains(RateLimitLockoutValidator.class.getName());
    }

    /**
     * Complemento do anterior: cobre a mecanica — {@code BeanFactoryPostProcessor} registrado,
     * {@code Environment} injetado antes de {@code postProcessBeanFactory} e o refresh abortando de
     * fato, em vez de so logar.
     */
    @Test
    void contextoNaoSobeComAInvarianteQuebrada() {
        try (AnnotationConfigApplicationContext contexto = new AnnotationConfigApplicationContext()) {
            contexto.getEnvironment()
                    .getPropertySources()
                    .addFirst(new MockPropertySource()
                            .withProperty(RateLimitLockoutValidator.MAX_ATTEMPTS, "5")
                            .withProperty(RateLimitLockoutValidator.LOGIN_POR_IP, "5"));
            contexto.register(RateLimitLockoutValidator.class);

            assertThatThrownBy(contexto::refresh)
                    .hasMessageContaining(RateLimitLockoutValidator.LOGIN_POR_IP)
                    .hasMessageContaining(RateLimitLockoutValidator.MAX_ATTEMPTS);
        }
    }

    /**
     * A armadilha do desenho: o validador le do {@code Environment}, que so tem a property quando
     * ela foi declarada, enquanto o runtime cai no default do POJO. Se os dois defaults divergirem,
     * o validador aprova uma configuracao diferente da que o bind vai montar — validando um mundo
     * que nao existe. Este teste e o unico ponto que impede a deriva.
     */
    @Test
    void defaultsDoValidadorSaoOsDefaultsDosPojos() {
        assertThat(RateLimitProperties.DEFAULT_POR_MINUTO_POR_IP)
                .as("default lido pelo validador quando a property esta ausente")
                .isEqualTo(new RateLimitProperties().getLoginPerMinutePerIp())
                .isEqualTo(new RateLimitProperties().getTotpVerifyPerMinutePerIp());
        assertThat(LockoutProperties.DEFAULT_MAX_ATTEMPTS).isEqualTo(new LockoutProperties().getMaxAttempts());
    }

    /**
     * Os defaults precisam satisfazer a propria invariante. Ate a Sprint 33 nao satisfaziam — POJOs
     * em 5 e 5 —, e so o {@code application.yml} (10) segurava; um contexto sem o YAML nascia com o
     * {@code 429} mascarando o {@code 423}.
     */
    @Test
    void defaultsDosPojosSatisfazemAInvariante() {
        assertThat(RateLimitProperties.DEFAULT_POR_MINUTO_POR_IP).isGreaterThan(LockoutProperties.DEFAULT_MAX_ATTEMPTS);
    }
}
