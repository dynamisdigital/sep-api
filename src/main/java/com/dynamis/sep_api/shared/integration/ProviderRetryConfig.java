package com.dynamis.sep_api.shared.integration;

import com.fasterxml.jackson.core.JacksonException;
import io.github.resilience4j.common.retry.configuration.RetryConfigCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;

/**
 * Predicate de retry compartilhado dos providers externos (Sprint 32 Task 32.3): retry somente em
 * falha <strong>transiente</strong>. O {@code retryExceptions} do YAML nao alcanca timeouts de
 * leitura, que o {@code RestClient} lanca como {@code RestClientException} generica ("Error while
 * extracting response") com causa {@link IOException} — este predicate os torna retryaveis, sem
 * reentrar em 4xx/5xx traduzidos ({@link RestClientResponseException}, cobertos pelo YAML) nem em
 * parsing invalido (Jackson e {@link IOException}, dai a exclusao explicita de
 * {@link JacksonException}).
 *
 * <p>O predicate e combinado por OR com o {@code retryExceptions} do YAML pelo Resilience4j.
 */
@Configuration
public class ProviderRetryConfig {

    @Bean
    public RetryConfigCustomizer celcoinKycRetryCustomizer() {
        return customizer("celcoin-kyc");
    }

    @Bean
    public RetryConfigCustomizer celcoinKybRetryCustomizer() {
        return customizer("celcoin-kyb");
    }

    @Bean
    public RetryConfigCustomizer celcoinBackgroundCheckRetryCustomizer() {
        return customizer("celcoin-background-check");
    }

    private static RetryConfigCustomizer customizer(String instancia) {
        return RetryConfigCustomizer.of(
                instancia, builder -> builder.retryOnException(falha -> transiente((Throwable) falha)));
    }

    /** Visivel para teste: timeout/IO reentra; resposta HTTP traduzida e parsing invalido nao. */
    static boolean transiente(Throwable falha) {
        if (falha instanceof RestClientResponseException) {
            return false;
        }
        if (temCausa(falha, JacksonException.class)) {
            return false;
        }
        return temCausa(falha, IOException.class);
    }

    private static boolean temCausa(Throwable falha, Class<? extends Throwable> tipo) {
        for (Throwable atual = falha; atual != null; atual = atual.getCause()) {
            if (tipo.isInstance(atual)) {
                return true;
            }
            if (atual.getCause() == atual) {
                break;
            }
        }
        return false;
    }
}
