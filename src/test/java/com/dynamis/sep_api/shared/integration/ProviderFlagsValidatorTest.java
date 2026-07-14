package com.dynamis.sep_api.shared.integration;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProviderFlagsValidatorTest {

    @Test
    void semPropriedades_defaultsFakePassam() {
        assertThatCode(() -> ProviderFlagsValidator.validar(new MockEnvironment()))
                .doesNotThrowAnyException();
    }

    @Test
    void valoresExternosValidos_passam() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("app.kyc.provider", "celcoin")
                .withProperty("app.kyb.provider", "celcoin")
                .withProperty("app.pld.provider", "celcoin")
                .withProperty("app.assinatura.provider", "clicksign")
                .withProperty("app.pix.provider", "celcoin")
                .withProperty("app.escrow.provider", "celcoin");

        assertThatCode(() -> ProviderFlagsValidator.validar(env)).doesNotThrowAnyException();
    }

    @Test
    void valorDesconhecido_falhaComPropertyValorEAceitos() {
        MockEnvironment env = new MockEnvironment().withProperty("app.pix.provider", "celocin");

        assertThatThrownBy(() -> ProviderFlagsValidator.validar(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.pix.provider")
                .hasMessageContaining("celocin")
                .hasMessageContaining("celcoin")
                .hasMessageContaining("fake");
    }

    @Test
    void assinaturaNaoAceitaCelcoin_adr0013() {
        MockEnvironment env = new MockEnvironment().withProperty("app.assinatura.provider", "celcoin");

        assertThatThrownBy(() -> ProviderFlagsValidator.validar(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.assinatura.provider")
                .hasMessageContaining("clicksign");
    }

    @Test
    void capacidadesCelcoinNaoAceitamClicksign() {
        MockEnvironment env = new MockEnvironment().withProperty("app.kyc.provider", "clicksign");

        assertThatThrownBy(() -> ProviderFlagsValidator.validar(env)).isInstanceOf(IllegalStateException.class);
    }
}
