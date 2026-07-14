package com.dynamis.sep_api.shared.integration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.Environment;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guard do profile {@code local-wiremock} (Sprint 32 Task 32.6): garante que o profile opt-in
 * seleciona os adapters HTTP explicitamente, aponta exclusivamente para {@code localhost} e usa
 * credenciais ficticias — e que o profile {@code test} continua com todos os providers em fake.
 * Teste de wiring/configuracao (sem busca fragil de strings em codigo).
 */
class LocalWiremockProfileTest {

    private static final List<String> BASE_URLS = List.of(
            "app.assinatura.clicksign.base-url",
            "app.celcoin.kyc.base-url",
            "app.celcoin.kyb.base-url",
            "app.celcoin.background-check.base-url",
            "app.celcoin.pix.base-url",
            "app.celcoin.escrow.base-url");

    private static final Map<String, String> PROVIDERS_ESPERADOS = Map.of(
            "app.kyc.provider", "celcoin",
            "app.kyb.provider", "celcoin",
            "app.pld.provider", "celcoin",
            "app.assinatura.provider", "clicksign",
            "app.pix.provider", "celcoin",
            "app.escrow.provider", "celcoin");

    private ApplicationContextRunner comProfile(String profile) {
        return new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("spring.profiles.active=" + profile);
    }

    @Test
    void localWiremock_selecionaAdaptersEApontaSomenteParaLocalhost() {
        comProfile("local-wiremock").run(ctx -> {
            Environment env = ctx.getEnvironment();
            PROVIDERS_ESPERADOS.forEach((property, esperado) ->
                    assertThat(env.getProperty(property)).as(property).isEqualTo(esperado));
            for (String property : BASE_URLS) {
                assertThat(env.getProperty(property)).as(property).startsWith("http://localhost:");
            }
        });
    }

    @Test
    void localWiremock_usaSomenteCredenciaisFicticias() {
        comProfile("local-wiremock").run(ctx -> {
            Environment env = ctx.getEnvironment();
            List<String> segredos = List.of(
                    "app.assinatura.clicksign.access-token",
                    "app.celcoin.kyc.client-secret",
                    "app.celcoin.kyb.client-secret",
                    "app.celcoin.background-check.client-secret",
                    "app.celcoin.pix.client-secret",
                    "app.celcoin.escrow.client-secret");
            for (String property : segredos) {
                assertThat(env.getProperty(property))
                        .as(property)
                        .contains("local-wiremock")
                        .contains("fictici");
            }
        });
    }

    @Test
    void profileTest_mantemTodosOsProvidersEmFake() {
        comProfile("test").run(ctx -> {
            Environment env = ctx.getEnvironment();
            PROVIDERS_ESPERADOS.keySet().forEach(property -> assertThat(env.getProperty(property, "fake"))
                    .as(property + " deve ser fake no profile test")
                    .isEqualTo("fake"));
        });
    }

    @Test
    void profileDev_mantemTodosOsProvidersEmFake() {
        comProfile("dev").run(ctx -> {
            Environment env = ctx.getEnvironment();
            PROVIDERS_ESPERADOS.keySet().forEach(property -> assertThat(env.getProperty(property, "fake"))
                    .as(property + " deve ser fake no profile dev")
                    .isEqualTo("fake"));
        });
    }

    @Test
    void localWiremock_naoEHerdadoPorOutrosProfiles() {
        // Bind direto do bloco app.*.provider nos profiles padrao: nenhum traz valor "celcoin".
        comProfile("test").run(ctx -> {
            Binder binder = Binder.get(ctx.getEnvironment());
            Map<String, Object> app = binder.bind("app", Bindable.mapOf(String.class, Object.class))
                    .orElse(Map.of());
            assertThat(app.toString()).doesNotContain("local-wiremock");
        });
    }
}
