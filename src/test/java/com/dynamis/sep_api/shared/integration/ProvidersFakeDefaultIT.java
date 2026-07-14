package com.dynamis.sep_api.shared.integration;

import com.dynamis.sep_api.contratos.application.port.out.AssinaturaDigitalProvider;
import com.dynamis.sep_api.contratos.infrastructure.adapter.assinatura.FakeAssinaturaDigitalProvider;
import com.dynamis.sep_api.escrow.application.port.out.EscrowProvider;
import com.dynamis.sep_api.escrow.infrastructure.adapter.fake.FakeEscrowProvider;
import com.dynamis.sep_api.onboarding.application.port.out.BackgroundCheckProvider;
import com.dynamis.sep_api.onboarding.application.port.out.KybProvider;
import com.dynamis.sep_api.onboarding.application.port.out.KycProvider;
import com.dynamis.sep_api.onboarding.infrastructure.adapter.celcoin.FakeBackgroundCheckProvider;
import com.dynamis.sep_api.onboarding.infrastructure.adapter.celcoin.FakeKybProvider;
import com.dynamis.sep_api.onboarding.infrastructure.adapter.celcoin.FakeKycProvider;
import com.dynamis.sep_api.pix.application.port.out.PixProvider;
import com.dynamis.sep_api.pix.infrastructure.adapter.fake.FakePixProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guard do contexto completo no profile {@code test} (Sprint 32 Task 32.6): cada port de provider
 * externo tem exatamente UM bean e ele e o fake — o boot de dev/test jamais ativa adapter HTTP.
 */
@SpringBootTest
@ActiveProfiles("test")
class ProvidersFakeDefaultIT {

    @Autowired
    private ApplicationContext contexto;

    @Test
    void cadaPortTemExatamenteUmBeanEEleEOFake() {
        assertSingleFake(KycProvider.class, FakeKycProvider.class);
        assertSingleFake(KybProvider.class, FakeKybProvider.class);
        assertSingleFake(BackgroundCheckProvider.class, FakeBackgroundCheckProvider.class);
        assertSingleFake(AssinaturaDigitalProvider.class, FakeAssinaturaDigitalProvider.class);
        assertSingleFake(PixProvider.class, FakePixProvider.class);
        assertSingleFake(EscrowProvider.class, FakeEscrowProvider.class);
    }

    private void assertSingleFake(Class<?> port, Class<?> fakeEsperado) {
        var beans = contexto.getBeansOfType(port);
        assertThat(beans).as(port.getSimpleName()).hasSize(1);
        assertThat(beans.values().iterator().next())
                .as(port.getSimpleName() + " deve ser o fake no profile test")
                .isInstanceOf(fakeEsperado);
    }
}
