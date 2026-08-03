package com.dynamis.sep_api.identity.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

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
