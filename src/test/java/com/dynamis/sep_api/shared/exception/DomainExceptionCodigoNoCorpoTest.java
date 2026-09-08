package com.dynamis.sep_api.shared.exception;

import com.dynamis.sep_api.shared.integration.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Arrays;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Matriz por subtipo selado de {@link DomainException} (Sprint 36 Task 36.2).
 *
 * <p><b>O valor esperado e literal, nao vem de {@code getCodigo()}.</b> Derivar a expectativa da
 * propria fonte faria uma implementacao que devolve o codigo errado concordar com o teste — o
 * mutante "trocar o codigo entre dois subtipos" passaria despercebido.
 *
 * <p><b>Codigo e {@code traceId} sao assertados juntos</b>, por exigencia do review da Task 36.1:
 * {@code ErrorResponseDto.of} tem cinco {@code String} posicionais consecutivas, entao um call site
 * que troque os dois compila em silencio. Assertar so o codigo deixaria essa troca viva.
 */
class DomainExceptionCodigoNoCorpoTest {

    private static final String TRACE = "trace-abc";
    private static final String PATH = "/api/v1/test";

    private final ApiExceptionHandler handler = new ApiExceptionHandler();
    private final HttpServletRequest request = Mockito.mock(HttpServletRequest.class);

    @BeforeEach
    void setUp() {
        Mockito.when(request.getRequestURI()).thenReturn(PATH);
        MDC.put(CorrelationIdFilter.MDC_KEY, TRACE);
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    static Stream<Arguments> subtiposSelados() {
        return Stream.of(
                Arguments.of(
                        new ValidacaoException("USR-400-001", "senha atual incorreta"),
                        HttpStatus.BAD_REQUEST,
                        "USR-400-001"),
                Arguments.of(
                        new RecursoNaoEncontradoException("ONB-404-001", "onboarding nao encontrado"),
                        HttpStatus.NOT_FOUND,
                        "ONB-404-001"),
                Arguments.of(
                        new ConflitoException("COB-409-001", "parcela em estado invalido"),
                        HttpStatus.CONFLICT,
                        "COB-409-001"),
                Arguments.of(
                        new AcessoNegadoException("CRD-403-001", "proposta de outro tomador"),
                        HttpStatus.FORBIDDEN,
                        "CRD-403-001"),
                Arguments.of(
                        new OperacaoNaoProcessavelException("CTR-422-001", "contrato nao assinado"),
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "CTR-422-001"));
    }

    @ParameterizedTest(name = "{0} -> {1} com codigo {2}")
    @MethodSource("subtiposSelados")
    void cadaSubtipoSeladoEmiteOProprioCodigo(DomainException excecao, HttpStatus status, String codigoEsperado) {
        ResponseEntity<ErrorResponseDto> resposta = handler.handleDomain(excecao, request);

        assertThat(resposta.getStatusCode()).isEqualTo(status);
        assertThat(resposta.getBody()).isNotNull();
        assertThat(resposta.getBody().codigo()).isEqualTo(codigoEsperado);
        assertThat(resposta.getBody().traceId()).isEqualTo(TRACE);
        assertThat(resposta.getBody().status()).isEqualTo(status.value());
        assertThat(resposta.getBody().error()).isEqualTo(status.getReasonPhrase());
        assertThat(resposta.getBody().message()).isEqualTo(excecao.getMessage());
        assertThat(resposta.getBody().path()).isEqualTo(PATH);
    }

    /**
     * O {@code switch} de {@code handleDomain} e exaustivo por construcao; a matriz acima nao e. Sem
     * esta guarda, um sexto subtipo permitido entraria na hierarquia, seria roteado pelo handler e
     * ficaria sem nenhum caso de teste — e a suite seguiria verde.
     */
    @Test
    void aMatrizCobreTodosOsSubtiposPermitidos() {
        var permitidos = Arrays.stream(DomainException.class.getPermittedSubclasses())
                .map(Class::getSimpleName)
                .toList();

        var cobertos = subtiposSelados()
                .map(argumentos -> argumentos.get()[0].getClass().getSimpleName())
                .toList();

        assertThat(cobertos).containsExactlyInAnyOrderElementsOf(permitidos);
    }

    /**
     * Handler sem taxonomia continua entregando o corpo de antes da sprint. Sao 13 dos 17 medidos no
     * Gate 36.0; o {@code handleAuth} representa a classe toda, porque todos passam pelo mesmo
     * {@code build}.
     */
    @Test
    void handlerSemCodigoNaoPassaAEmitirOCampo() {
        ResponseEntity<ErrorResponseDto> resposta = handler.handleAccessDenied(
                new org.springframework.security.access.AccessDeniedException("negado"), request);

        assertThat(resposta.getBody()).isNotNull();
        assertThat(resposta.getBody().codigo()).isNull();
        assertThat(resposta.getBody().traceId()).isEqualTo(TRACE);
    }
}
