package com.dynamis.sep_api.identity.infrastructure.security;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Fail-fast da invariante {@code rate-limit > lockout.max-attempts} (Sprint 34 Task 34.4).
 *
 * <p>O {@link RateLimitFilter} roda antes do controller. Se o limite por IP nao for
 * <b>estritamente</b> maior que o limiar de lockout, a tentativa seguinte ao bloqueio — a unica
 * capaz de responder {@code 423} — e barrada com {@code 429}, e o usuario legitimo nunca descobre
 * que a conta esta bloqueada. Foi exatamente o que aconteceu ate a Sprint 33, com os dois valores em
 * 5. A regra vivia so num comentario do {@code application.yml} e num assert da
 * {@code LockoutLoginIT}, que le os valores <b>efetivos</b>: as cinco env vars podiam quebra-la em
 * silencio em qualquer ambiente sem ninguem perceber.
 *
 * <p>{@link BeanFactoryPostProcessor} pelo mesmo motivo do {@code ProviderFlagsValidator}: roda
 * antes da instanciacao dos singletons, entao a mensagem clara vence a corrida contra o sintoma
 * confuso — aqui, um {@code 429} inexplicavel em producao meses depois.
 *
 * <p>Le do {@link Environment} em vez de injetar {@link RateLimitProperties} e
 * {@link LockoutProperties}: um {@code BeanFactoryPostProcessor} roda <b>antes</b> do bind de
 * {@code @ConfigurationProperties}, entao os POJOs ainda nao existem preenchidos.
 */
@Component
public class RateLimitLockoutValidator implements BeanFactoryPostProcessor, EnvironmentAware {

    static final String MAX_ATTEMPTS = "app.security.lockout.max-attempts";
    static final String LOGIN_POR_IP = "app.security.rate-limit.login-per-minute-per-ip";
    static final String TOTP_VERIFY_POR_IP = "app.security.rate-limit.totp-verify-per-minute-per-ip";

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        validar(environment);
    }

    /**
     * Os defaults precisam ser os dos POJOs, nao os do {@code application.yml}: se a property
     * estiver ausente e o validador assumir o valor do YAML, ele aprova uma configuracao que o bind
     * vai montar de outro jeito — validando um mundo diferente do que o runtime recebe.
     * {@code RateLimitPropertiesTest} trava o par contra deriva.
     */
    static void validar(Environment environment) {
        int maxAttempts = environment.getProperty(MAX_ATTEMPTS, Integer.class, LockoutProperties.DEFAULT_MAX_ATTEMPTS);
        for (String limitePorIp : List.of(LOGIN_POR_IP, TOTP_VERIFY_POR_IP)) {
            int limite =
                    environment.getProperty(limitePorIp, Integer.class, RateLimitProperties.DEFAULT_POR_MINUTO_POR_IP);
            if (limite <= maxAttempts) {
                throw new IllegalStateException(limitePorIp + "=" + limite + " deve ser estritamente maior que "
                        + MAX_ATTEMPTS + "=" + maxAttempts
                        + "; com o limite por IP menor ou igual ao limiar de lockout o 429 mascara o 423 e a"
                        + " conta bloqueada fica indistinguivel de excesso de requisicoes.");
            }
        }
    }
}
