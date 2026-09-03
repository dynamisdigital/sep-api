package com.dynamis.sep_api.shared.integration;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
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
     * O {@code logback-spring.xml} repete o nome da chave <b>duas vezes em XML</b>: no pattern do
     * console ({@code %X{...}}) e no {@code includeMdcKeyName} do encoder JSON.
     *
     * <p>A direcao que <b>so este teste</b> cobre e a de <b>editar o XML</b>: derrubar o
     * {@code includeMdcKeyName} ou mexer no pattern apaga o campo do log estruturado sem erro de
     * boot e sem teste vermelho, e a constante Java fica intacta e inocente. A direcao contraria —
     * renomear a constante — ja e coberta por {@link #nomesPublicosDaCorrelacaoSaoEstaveis()}.
     *
     * <p>Le pelo classpath, e nao por caminho relativo: o recurso ja esta em
     * {@code build/resources/main}, e ler por {@code src/main/resources/...} amarra o teste ao
     * diretorio de trabalho da JVM.
     */
    @Test
    void chaveDeMdcCasaComOQueOLogbackConsome() throws Exception {
        String logback;
        try (var recurso = getClass().getClassLoader().getResourceAsStream("logback-spring.xml")) {
            assertThat(recurso)
                    .as("logback-spring.xml precisa estar no classpath de teste")
                    .isNotNull();
            logback = new String(recurso.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(logback)
                .as("pattern do console le a chave por %%X{...}; o ':-}' fecha o placeholder inteiro"
                        + " e impede que um prefixo da chave case por acidente")
                .contains("%X{" + CorrelationIdFilter.MDC_KEY + ":-}");
        assertThat(logback)
                .as("encoder JSON so inclui a chave se ela estiver nomeada aqui")
                .contains("<includeMdcKeyName>" + CorrelationIdFilter.MDC_KEY + "</includeMdcKeyName>");
    }

    /**
     * Os dois nomes sao estaveis, mas com publicos diferentes: {@code HEADER} e contrato com o
     * cliente HTTP, e {@code MDC_KEY} e contrato com a operacao — nenhum cliente o ve; quem o consome
     * e o Logback e o coletor de log.
     *
     * <p>Medido na Sprint 35 Task 35.6: antes destes pinos, trocar {@code MDC_KEY} por outro valor
     * passava a suite inteira (2253 testes) sem uma falha. Consolidar os call sites do Java na
     * constante nao resolvia — o literal que restava estava fora do Java.
     */
    @Test
    void nomesPublicosDaCorrelacaoSaoEstaveis() {
        assertThat(CorrelationIdFilter.HEADER).isEqualTo("X-Correlation-Id");
        assertThat(CorrelationIdFilter.MDC_KEY).isEqualTo("correlationId");
    }
}
