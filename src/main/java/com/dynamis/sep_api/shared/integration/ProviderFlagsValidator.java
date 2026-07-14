package com.dynamis.sep_api.shared.integration;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
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
 * <p>Implementado como {@link BeanFactoryPostProcessor}: roda <strong>antes</strong> da
 * instanciacao de qualquer singleton (adapters e consumidores), garantindo que a mensagem clara
 * vence a corrida contra o erro confuso de bean ausente, independente da ordem de criacao.
 *
 * <p>Defaults sao {@code fake} — a ausencia da property nunca falha. {@code
 * app.assinatura.provider} aceita {@code clicksign} e <strong>nao</strong> aceita {@code celcoin}
 * (ADR 0013). Flags adjacentes ({@code app.open-finance.provider}, {@code
 * app.notificacoes.provider}) ficam fora do escopo do ADR 0017 e seguem apenas a selecao por
 * {@code @ConditionalOnProperty}. A mensagem cita apenas nomes de property e valores de flag —
 * nunca credenciais.
 */
@Component
public class ProviderFlagsValidator implements BeanFactoryPostProcessor, EnvironmentAware {

    private static final Set<String> FAKE_OU_CELCOIN = Set.of("fake", "celcoin");

    private static final Map<String, Set<String>> FLAGS = Map.of(
            "app.kyc.provider", FAKE_OU_CELCOIN,
            "app.kyb.provider", FAKE_OU_CELCOIN,
            "app.pld.provider", FAKE_OU_CELCOIN,
            "app.assinatura.provider", Set.of("fake", "clicksign"),
            "app.pix.provider", FAKE_OU_CELCOIN,
            "app.escrow.provider", FAKE_OU_CELCOIN);

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
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
