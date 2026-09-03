package com.dynamis.sep_api.identity.infrastructure.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sprint 35 Task 35.2. Roda sobre o metodo estatico, sem contexto Spring — mesmo desenho do
 * {@link RateLimitLockoutValidatorTest}.
 */
class ProxyAllowlistValidatorTest {

    @Test
    void allowlistVazio_falhaCitandoAPropertyEAEnvVar() {
        assertThatThrownBy(() -> ProxyAllowlistValidator.validar(""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(ProxyAllowlistValidator.PROPERTY)
                .hasMessageContaining("APP_TRUSTED_PROXIES");
    }

    @Test
    void allowlistNulo_falha() {
        assertThatThrownBy(() -> ProxyAllowlistValidator.validar(null)).isInstanceOf(IllegalStateException.class);
    }

    /** Espaco em branco e o caso que um {@code APP_TRUSTED_PROXIES=" "} produz, e nao e allowlist. */
    @Test
    void allowlistSoComEspacos_falha() {
        assertThatThrownBy(() -> ProxyAllowlistValidator.validar("   ")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void allowlistPreenchido_passa() {
        assertThatCode(() -> ProxyAllowlistValidator.validar("10\\.0\\.\\d{1,3}\\.\\d{1,3}"))
                .doesNotThrowAnyException();
    }
}
