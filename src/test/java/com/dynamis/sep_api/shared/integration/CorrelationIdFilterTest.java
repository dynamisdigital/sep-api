package com.dynamis.sep_api.shared.integration;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void limparMdc() {
        MDC.clear();
    }

    @Test
    void preservaHeaderValidoDuranteRequestELimpaMdcAoFinal() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, "cliente-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> duranteRequest = new AtomicReference<>();
        FilterChain chain = (req, res) -> duranteRequest.set(MDC.get(CorrelationIdFilter.MDC_KEY));

        filter.doFilter(request, response, chain);

        assertThat(duranteRequest).hasValue("cliente-123");
        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo("cliente-123");
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void geraUuidQuandoHeaderAusente() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {});

        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).satisfies(value -> assertThatCodeIsUuid(value));
    }

    @Test
    void substituiHeaderComCaracteresInvalidos() throws Exception {
        MockHttpServletRequest request = mock(MockHttpServletRequest.class);
        when(request.getHeader(CorrelationIdFilter.HEADER)).thenReturn("abc\nforjado");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {});

        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).satisfies(value -> assertThatCodeIsUuid(value));
    }

    @Test
    void substituiHeaderMaiorQueLimite() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, "a".repeat(129));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {});

        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).satisfies(value -> assertThatCodeIsUuid(value));
    }

    private static void assertThatCodeIsUuid(String value) {
        assertThat(value).isNotBlank();
        assertThat(UUID.fromString(value).toString()).isEqualTo(value);
    }

    /**
     * O nome da chave de MDC nao e detalhe interno: o {@code logback-spring.xml} o repete <b>duas
     * vezes em XML</b>, uma no pattern do console ({@code %X{...}}) e outra no
     * {@code includeMdcKeyName} do encoder JSON. Renomear a constante deixaria os dois apontando
     * para uma chave que ninguem escreve — o campo some do log estruturado <b>em silencio</b>, sem
     * erro de boot e sem teste vermelho.
     *
     * <p>Medido na Sprint 35 Task 35.6: antes deste teste, trocar {@code MDC_KEY} por outro valor
     * passava a suite inteira (2253 testes) sem uma falha. Consolidar os call sites do Java na
     * constante nao resolvia isso — o literal duplicado que restava estava fora do Java.
     */
    @Test
    void chaveDeMdcCasaComOQueOLogbackConsome() throws Exception {
        String logback = Files.readString(Path.of("src/main/resources/logback-spring.xml"));

        assertThat(logback)
                .as("pattern do console le a chave por %%X{...}")
                .contains("%X{" + CorrelationIdFilter.MDC_KEY);
        assertThat(logback)
                .as("encoder JSON so inclui a chave se ela estiver nomeada aqui")
                .contains("<includeMdcKeyName>" + CorrelationIdFilter.MDC_KEY + "</includeMdcKeyName>");
    }

    /** O header de resposta e o par publico da chave; os dois nomes sao contrato com o cliente. */
    @Test
    void headerPublicoEhOEsperadoPelosConsumidores() {
        assertThat(CorrelationIdFilter.HEADER).isEqualTo("X-Correlation-Id");
        assertThat(CorrelationIdFilter.MDC_KEY).isEqualTo("correlationId");
    }
}
