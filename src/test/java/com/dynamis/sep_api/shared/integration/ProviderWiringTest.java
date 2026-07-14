package com.dynamis.sep_api.shared.integration;

import com.dynamis.sep_api.contratos.application.port.out.AssinaturaDigitalProvider;
import com.dynamis.sep_api.contratos.infrastructure.adapter.assinatura.ClicksignAssinaturaDigitalProvider;
import com.dynamis.sep_api.contratos.infrastructure.adapter.assinatura.ClicksignAssinaturaMapper;
import com.dynamis.sep_api.contratos.infrastructure.adapter.assinatura.ClicksignAssinaturaProperties;
import com.dynamis.sep_api.contratos.infrastructure.adapter.assinatura.FakeAssinaturaDigitalProvider;
import com.dynamis.sep_api.escrow.application.port.out.EscrowProvider;
import com.dynamis.sep_api.escrow.infrastructure.adapter.celcoin.CelcoinEscrowOAuthTokenProvider;
import com.dynamis.sep_api.escrow.infrastructure.adapter.celcoin.CelcoinEscrowProperties;
import com.dynamis.sep_api.escrow.infrastructure.adapter.celcoin.CelcoinEscrowProvider;
import com.dynamis.sep_api.escrow.infrastructure.adapter.fake.FakeEscrowProvider;
import com.dynamis.sep_api.onboarding.application.port.out.KycProvider;
import com.dynamis.sep_api.onboarding.infrastructure.adapter.celcoin.CelcoinKycMapper;
import com.dynamis.sep_api.onboarding.infrastructure.adapter.celcoin.CelcoinKycProperties;
import com.dynamis.sep_api.onboarding.infrastructure.adapter.celcoin.CelcoinKycProvider;
import com.dynamis.sep_api.onboarding.infrastructure.adapter.celcoin.CelcoinOAuthTokenProvider;
import com.dynamis.sep_api.onboarding.infrastructure.adapter.celcoin.FakeKycProvider;
import com.dynamis.sep_api.pix.application.port.out.PixProvider;
import com.dynamis.sep_api.pix.infrastructure.adapter.PixWebhookNormalizer;
import com.dynamis.sep_api.pix.infrastructure.adapter.celcoin.CelcoinPixOAuthTokenProvider;
import com.dynamis.sep_api.pix.infrastructure.adapter.celcoin.CelcoinPixProperties;
import com.dynamis.sep_api.pix.infrastructure.adapter.celcoin.CelcoinPixProvider;
import com.dynamis.sep_api.pix.infrastructure.adapter.fake.FakePixProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Testes de wiring das feature flags de providers (Sprint 32 Task 32.2, ADR 0017). Provam com as
 * classes reais: fake default ({@code matchIfMissing} apenas no fake), selecao externa explicita,
 * valor desconhecido derrubando o boot com mensagem clara ({@link ProviderFlagsValidator}) e
 * fail-fast de configuracao obrigatoria sem expor segredo. Nenhum HTTP e executado — colaboradores
 * sao mocks.
 */
class ProviderWiringTest {

    private static final String SEGREDO = "segredo-que-nao-pode-vazar";

    private ApplicationContextRunner pixRunner(CelcoinPixProperties props) {
        return new ApplicationContextRunner()
                .withBean(PixWebhookNormalizer.class, () -> mock(PixWebhookNormalizer.class))
                .withBean(RestClientFactory.class, () -> mock(RestClientFactory.class))
                .withBean(CelcoinPixOAuthTokenProvider.class, () -> mock(CelcoinPixOAuthTokenProvider.class))
                .withBean(CelcoinPixProperties.class, () -> props)
                .withUserConfiguration(FakePixProvider.class, CelcoinPixProvider.class);
    }

    private ApplicationContextRunner escrowRunner(CelcoinEscrowProperties props) {
        return new ApplicationContextRunner()
                .withBean(RestClientFactory.class, () -> mock(RestClientFactory.class))
                .withBean(CelcoinEscrowOAuthTokenProvider.class, () -> mock(CelcoinEscrowOAuthTokenProvider.class))
                .withBean(CelcoinEscrowProperties.class, () -> props)
                .withUserConfiguration(FakeEscrowProvider.class, CelcoinEscrowProvider.class);
    }

    private ApplicationContextRunner assinaturaRunner(ClicksignAssinaturaProperties props) {
        return new ApplicationContextRunner()
                .withBean(RestClientFactory.class, () -> mock(RestClientFactory.class))
                .withBean(ClicksignAssinaturaMapper.class, () -> mock(ClicksignAssinaturaMapper.class))
                .withBean(ClicksignAssinaturaProperties.class, () -> props)
                .withUserConfiguration(FakeAssinaturaDigitalProvider.class, ClicksignAssinaturaDigitalProvider.class);
    }

    private ApplicationContextRunner kycRunner() {
        return new ApplicationContextRunner()
                .withBean(RestClientFactory.class, () -> mock(RestClientFactory.class))
                .withBean(CelcoinKycMapper.class, () -> mock(CelcoinKycMapper.class))
                .withBean(CelcoinOAuthTokenProvider.class, () -> mock(CelcoinOAuthTokenProvider.class))
                .withBean(CelcoinKycProperties.class, () -> new CelcoinKycProperties("http://localhost:9", "id", "s"))
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withUserConfiguration(FakeKycProvider.class, CelcoinKycProvider.class);
    }

    // ------------------------------------------------------------------ fake default

    @Test
    void semProperty_selecionaExatamenteUmFake() {
        pixRunner(pixProps("http://localhost:9")).run(ctx -> {
            assertThat(ctx).hasSingleBean(PixProvider.class);
            assertThat(ctx.getBean(PixProvider.class)).isInstanceOf(FakePixProvider.class);
        });
        escrowRunner(escrowProps("http://localhost:9")).run(ctx -> {
            assertThat(ctx).hasSingleBean(EscrowProvider.class);
            assertThat(ctx.getBean(EscrowProvider.class)).isInstanceOf(FakeEscrowProvider.class);
        });
        assinaturaRunner(clicksignProps("http://localhost:9", "token-ficticio")).run(ctx -> {
            assertThat(ctx).hasSingleBean(AssinaturaDigitalProvider.class);
            assertThat(ctx.getBean(AssinaturaDigitalProvider.class)).isInstanceOf(FakeAssinaturaDigitalProvider.class);
        });
        kycRunner().run(ctx -> {
            assertThat(ctx).hasSingleBean(KycProvider.class);
            assertThat(ctx.getBean(KycProvider.class)).isInstanceOf(FakeKycProvider.class);
        });
    }

    @Test
    void fakeNaoExigeOAuthCredencialNemFactory() {
        // Runner minimo: so o fake e sua dependencia direta — sem token provider, properties ou
        // factory. Se o fake exigisse credencial/OAuth, este contexto falharia.
        new ApplicationContextRunner()
                .withBean(PixWebhookNormalizer.class, () -> mock(PixWebhookNormalizer.class))
                .withUserConfiguration(FakePixProvider.class)
                .run(ctx -> assertThat(ctx).hasSingleBean(PixProvider.class));
        new ApplicationContextRunner()
                .withUserConfiguration(FakeEscrowProvider.class)
                .run(ctx -> assertThat(ctx).hasSingleBean(EscrowProvider.class));
    }

    // ------------------------------------------------------------------ selecao explicita

    @Test
    void valorExterno_selecionaExatamenteOAdapter() {
        pixRunner(pixProps("http://localhost:9"))
                .withPropertyValues("app.pix.provider=celcoin")
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(PixProvider.class);
                    assertThat(ctx.getBean(PixProvider.class)).isInstanceOf(CelcoinPixProvider.class);
                });
        escrowRunner(escrowProps("http://localhost:9"))
                .withPropertyValues("app.escrow.provider=celcoin")
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(EscrowProvider.class);
                    assertThat(ctx.getBean(EscrowProvider.class)).isInstanceOf(CelcoinEscrowProvider.class);
                });
        assinaturaRunner(clicksignProps("http://localhost:9", "token-ficticio"))
                .withPropertyValues("app.assinatura.provider=clicksign")
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(AssinaturaDigitalProvider.class);
                    assertThat(ctx.getBean(AssinaturaDigitalProvider.class))
                            .isInstanceOf(ClicksignAssinaturaDigitalProvider.class);
                });
        kycRunner().withPropertyValues("app.kyc.provider=celcoin").run(ctx -> {
            assertThat(ctx).hasSingleBean(KycProvider.class);
            assertThat(ctx.getBean(KycProvider.class)).isInstanceOf(CelcoinKycProvider.class);
        });
    }

    // ------------------------------------------------------------------ valor desconhecido

    @Test
    void valorDesconhecido_derrubaOBootComMensagemClara() {
        new ApplicationContextRunner()
                .withUserConfiguration(ProviderFlagsValidator.class)
                .withPropertyValues("app.pix.provider=celocin")
                .run(ctx -> {
                    assertThat(ctx).hasFailed();
                    assertThat(ctx.getStartupFailure())
                            .rootCause()
                            .hasMessageContaining("app.pix.provider")
                            .hasMessageContaining("celocin")
                            .hasMessageContaining("celcoin");
                });
    }

    @Test
    void assinaturaCelcoin_rejeitadaPeloValidator_adr0013() {
        new ApplicationContextRunner()
                .withUserConfiguration(ProviderFlagsValidator.class)
                .withPropertyValues("app.assinatura.provider=celcoin")
                .run(ctx -> {
                    assertThat(ctx).hasFailed();
                    assertThat(ctx.getStartupFailure())
                            .rootCause()
                            .hasMessageContaining("app.assinatura.provider")
                            .hasMessageContaining("clicksign");
                });
    }

    @Test
    void flagsValidas_validatorNaoImpedeBoot() {
        new ApplicationContextRunner()
                .withUserConfiguration(ProviderFlagsValidator.class)
                .withPropertyValues("app.pix.provider=celcoin", "app.assinatura.provider=clicksign")
                .run(ctx -> assertThat(ctx).hasNotFailed());
    }

    // ------------------------------------------------------------------ fail-fast sem segredo

    @Test
    void adapterSemBaseUrl_falhaCitandoAPropertySemExporSegredo() {
        pixRunner(pixProps(" ")).withPropertyValues("app.pix.provider=celcoin").run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure()).rootCause().hasMessageContaining("app.celcoin.pix.base-url");
            assertThat(ctx.getStartupFailure().getMessage()).doesNotContain(SEGREDO);
        });
        escrowRunner(escrowProps(" "))
                .withPropertyValues("app.escrow.provider=celcoin")
                .run(ctx -> {
                    assertThat(ctx).hasFailed();
                    assertThat(ctx.getStartupFailure()).rootCause().hasMessageContaining("app.celcoin.escrow.base-url");
                });
    }

    @Test
    void clicksignSemAccessToken_falhaSemExporToken() {
        assinaturaRunner(clicksignProps("http://localhost:9", " "))
                .withPropertyValues("app.assinatura.provider=clicksign")
                .run(ctx -> {
                    assertThat(ctx).hasFailed();
                    assertThat(ctx.getStartupFailure())
                            .rootCause()
                            .hasMessageContaining("app.assinatura.clicksign.access-token");
                    assertThat(ctx.getStartupFailure().getMessage()).doesNotContain(SEGREDO);
                });
    }

    // ------------------------------------------------------------------ helpers

    private static CelcoinPixProperties pixProps(String baseUrl) {
        return new CelcoinPixProperties(baseUrl, "client-ficticio", SEGREDO);
    }

    private static CelcoinEscrowProperties escrowProps(String baseUrl) {
        return new CelcoinEscrowProperties(baseUrl, "client-ficticio", SEGREDO);
    }

    private static ClicksignAssinaturaProperties clicksignProps(String baseUrl, String token) {
        return new ClicksignAssinaturaProperties(baseUrl, token, null);
    }
}
