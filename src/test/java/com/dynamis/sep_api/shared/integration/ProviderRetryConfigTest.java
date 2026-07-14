package com.dynamis.sep_api.shared.integration;

import com.fasterxml.jackson.core.JsonParseException;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderRetryConfigTest {

    @Test
    void timeoutDeLeituraEmbrulhadoEmRestClientException_ehTransiente() {
        RestClientException falha = new RestClientException(
                "Error while extracting response", new SocketTimeoutException("Read timed out"));

        assertThat(ProviderRetryConfig.transiente(falha)).isTrue();
    }

    @Test
    void ioDiretoOuResourceAccess_ehTransiente() {
        assertThat(ProviderRetryConfig.transiente(new IOException("broken pipe")))
                .isTrue();
        assertThat(ProviderRetryConfig.transiente(new ResourceAccessException("io", new IOException())))
                .isTrue();
    }

    @Test
    void parsingInvalido_naoReentra() {
        RestClientException falha = new RestClientException(
                "Error while extracting response", new JsonParseException(null, "corpo invalido"));

        assertThat(ProviderRetryConfig.transiente(falha)).isFalse();
    }

    @Test
    void respostasHttpTraduzidas_naoEntramPeloPredicate() {
        // 5xx reentra pelo retryExceptions do YAML; o predicate nao pode reintroduzir 4xx.
        assertThat(ProviderRetryConfig.transiente(
                        HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "bad", new HttpHeaders(), null, null)))
                .isFalse();
        assertThat(ProviderRetryConfig.transiente(HttpServerErrorException.create(
                        HttpStatus.INTERNAL_SERVER_ERROR, "err", new HttpHeaders(), null, null)))
                .isFalse();
    }

    @Test
    void faultHttpTraduzido_reentraApenasEm5xx() {
        com.dynamis.sep_api.contratos.application.port.out.exception.AssinaturaProviderHttpException erro5xx =
                new com.dynamis.sep_api.contratos.application.port.out.exception.AssinaturaProviderHttpException(
                        502, "Clicksign HTTP 502", null);
        com.dynamis.sep_api.contratos.application.port.out.exception.AssinaturaProviderHttpException erro4xx =
                new com.dynamis.sep_api.contratos.application.port.out.exception.AssinaturaProviderHttpException(
                        422, "Clicksign HTTP 422", null);

        assertThat(ProviderRetryConfig.transiente(erro5xx)).isTrue();
        assertThat(ProviderRetryConfig.transiente(erro4xx)).isFalse();
    }

    @Test
    void cadeiaDeCausaCircular_naoEntraEmLoopInfinito() {
        RuntimeException a = new RuntimeException("a");
        RuntimeException b = new RuntimeException("b", a);
        a.initCause(b); // ciclo a -> b -> a

        assertThat(ProviderRetryConfig.transiente(a)).isFalse();
    }

    @Test
    void excecaoSemCausaIo_naoReentra() {
        assertThat(ProviderRetryConfig.transiente(new IllegalStateException("resposta invalida")))
                .isFalse();
        assertThat(ProviderRetryConfig.transiente(new DataBufferLimitException("limite")))
                .isFalse();
    }
}
