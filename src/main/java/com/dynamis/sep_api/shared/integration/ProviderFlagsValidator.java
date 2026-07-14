package com.dynamis.sep_api.shared.integration;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Fail-fast das feature flags de providers (Sprint 32 Task 32.2, ADR 0017): valor desconhecido de
 * flag derruba o boot com mensagem clara (property, valor recebido, valores aceitos) em vez de
 * deixar o port sem bean e falhar como {@code NoSuchBeanDefinitionException} no consumidor.
 *
 * <p>A validacao roda na construcao do bean (context refresh). Defaults sao {@code fake} — a
 * ausencia da property nunca falha. {@code app.assinatura.provider} aceita {@code clicksign} e
 * <strong>nao</strong> aceita {@code celcoin} (ADR 0013). A mensagem cita apenas nomes de
 * property e valores de flag — nunca credenciais.
 */
@Component
public class ProviderFlagsValidator {

    private static final Set<String> FAKE_OU_CELCOIN = Set.of("fake", "celcoin");

    private static final Map<String, Set<String>> FLAGS = Map.of(
            "app.kyc.provider", FAKE_OU_CELCOIN,
            "app.kyb.provider", FAKE_OU_CELCOIN,
            "app.pld.provider", FAKE_OU_CELCOIN,
            "app.assinatura.provider", Set.of("fake", "clicksign"),
            "app.pix.provider", FAKE_OU_CELCOIN,
            "app.escrow.provider", FAKE_OU_CELCOIN);

    public ProviderFlagsValidator(Environment environment) {
        validar(environment);
    }

    static void validar(Environment environment) {
        for (Map.Entry<String, Set<String>> flag : FLAGS.entrySet()) {
            String valor = environment.getProperty(flag.getKey(), "fake");
            if (!flag.getValue().contains(valor)) {
                throw new IllegalStateException(flag.getKey() + "='" + valor + "' invalido; valores aceitos: "
                        + new TreeSet<>(flag.getValue())
                        + ". Fake e o default; adapter HTTP exige selecao explicita (ADR 0017).");
            }
        }
    }
}
