package com.dynamis.sep_api.shared.exception;

import com.dynamis.sep_api.backoffice.domain.exception.LimiteReprocessoExcedidoException;
import com.dynamis.sep_api.backoffice.domain.exception.TipoReprocessoNaoSuportadoException;
import com.dynamis.sep_api.backoffice.domain.vo.TipoChamadaProvider;
import com.dynamis.sep_api.contratos.application.service.ccb.CcbGeracaoException;
import com.dynamis.sep_api.credito.domain.exception.OwnershipPropostaException;
import com.dynamis.sep_api.identity.application.exception.ContaBloqueadaException;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;

import java.time.Duration;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Matriz final de regressao do corpo de erro (Sprint 36 Task 36.6).
 *
 * <p>Consolida numa tabela unica o que as Tasks 36.2 a 36.4 provaram em separado, e acrescenta a
 * coluna que nenhuma delas tinha: <b>se o codigo emitido esta no catalogo publicado</b>. Foi essa
 * coluna que revelou que a propagacao do {@code ex.getCodigo()} entregava ao cliente codigos que o
 * contrato nao declara — ver {@link #codigoForaDoPerimetroNaoVazaParaOCorpo()}.
 *
 * <p>Os testes por Task <b>continuam existindo</b> e nao sao redundantes: eles provam independencia
 * entre {@code codigo} e {@code Retry-After}, e exatidao por subtipo. Esta matriz prova a superficie
 * inteira de uma vez.
 */
class MatrizFinalDeErroTest {

    private static final String TRACE = "trace-matriz";
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

    /**
     * Cada linha e {@code rotulo | invocacao do handler | status | codigo esperado (null = ausente) |
     * Retry-After esperado (null = ausente)}.
     */
    static Stream<Arguments> matriz() {
        return Stream.of(
                linha(
                        "ValidacaoException",
                        h -> h.apply(new ValidacaoException("GOV-400-001", "m")),
                        400,
                        "GOV-400-001",
                        null),
                linha(
                        "RecursoNaoEncontrado",
                        h -> h.apply(new RecursoNaoEncontradoException("USR-404-001", "m")),
                        404,
                        "USR-404-001",
                        null),
                linha(
                        "ConflitoException",
                        h -> h.apply(new ConflitoException("COB-409-001", "m")),
                        409,
                        "COB-409-001",
                        null),
                linha(
                        "AcessoNegadoException",
                        h -> h.apply(new AcessoNegadoException("USR-403-001", "m")),
                        403,
                        "USR-403-001",
                        null),
                linha(
                        "OperacaoNaoProcessavel",
                        h -> h.apply(new OperacaoNaoProcessavelException("CTR-422-001", "m")),
                        422,
                        "CTR-422-001",
                        null),
                linha("CcbGeracao (36.3)", h -> h.apply(new CcbGeracaoException("m", null)), 422, "CTR-422-004", null),
                linha("Ownership (excluido)", h -> h.apply(new OwnershipPropostaException("m")), 403, null, null));
    }

    private static Arguments linha(
            String rotulo,
            Function<Function<DomainException, ResponseEntity<ErrorResponseDto>>, ResponseEntity<ErrorResponseDto>>
                    invocacao,
            int status,
            String codigo,
            String retryAfter) {
        return Arguments.of(rotulo, invocacao, status, codigo, retryAfter);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("matriz")
    void matrizDeCorpoDeErro(
            String rotulo,
            Function<Function<DomainException, ResponseEntity<ErrorResponseDto>>, ResponseEntity<ErrorResponseDto>>
                    invocacao,
            int status,
            String codigoEsperado,
            String retryAfterEsperado) {
        ResponseEntity<ErrorResponseDto> resposta = invocacao.apply(excecao -> handler.handleDomain(excecao, request));

        conferir(resposta, status, codigoEsperado, retryAfterEsperado);
    }

    @Test
    void contaBloqueadaTem423CodigoCatalogadoERetryAfter() {
        ResponseEntity<ErrorResponseDto> resposta =
                handler.handleLocked(new ContaBloqueadaException(Duration.ofSeconds(615)), request);

        conferir(resposta, 423, "AUTH-423-001", "615");
    }

    @Test
    void limiteReprocessoTem429ComCodigoCatalogado() {
        ResponseEntity<ErrorResponseDto> resposta =
                handler.handleLimiteReprocesso(new LimiteReprocessoExcedidoException("e"), request);

        conferir(resposta, 429, "BOF-429-001", null);
    }

    @Test
    void tipoReprocessoTem400ComCodigoCatalogado() {
        ResponseEntity<ErrorResponseDto> resposta =
                handler.handleTipoReprocesso(new TipoReprocessoNaoSuportadoException(TipoChamadaProvider.KYC), request);

        conferir(resposta, 400, "BOF-400-002", null);
    }

    /** Handler deliberadamente sem taxonomia: 1 dos 13 medidos no Gate 36.0. */
    @Test
    void handlerSemTaxonomiaOmiteOCampo() {
        ResponseEntity<ErrorResponseDto> resposta =
                handler.handleAuth(new BadCredentialsException("credenciais"), request);

        conferir(resposta, 401, null, null);
    }

    /**
     * O caso que a matriz revelou. {@code OwnershipPropostaException} carrega {@code CRD-403-001},
     * que o Gate 36.0 excluiu por <b>colisao</b>: o mesmo valor identifica "proposta de outro
     * tomador" no modulo credito e "credora de outro dono" no modulo credores. Ele herda de
     * {@code AcessoNegadoException}, entao chega ao {@code handleDomain} como qualquer outro.
     *
     * <p>Sem filtro, o corpo entregaria ao cliente um valor que o {@code enum} do OpenAPI nao
     * declara — resposta violando o proprio schema publicado — e ainda por cima um identificador
     * ambiguo, que e exatamente o que a §Decisao tecnica principal da Spec 036 proibe: "todo codigo
     * que falha em qualquer um dos tres criterios simplesmente nao emite codigo".
     */
    @Test
    void codigoForaDoPerimetroNaoVazaParaOCorpo() {
        ResponseEntity<ErrorResponseDto> resposta =
                handler.handleDomain(new OwnershipPropostaException("proposta de outro tomador"), request);

        assertThat(resposta.getBody()).isNotNull();
        assertThat(resposta.getBody().codigo())
                .as("CRD-403-001 esta fora do catalogo por colisao; emiti-lo publicaria identificador"
                        + " ambiguo e violaria o enum declarado no contrato")
                .isNull();
        assertThat(CatalogoCodigosErro.publicados()).doesNotContain("CRD-403-001");
    }

    private void conferir(ResponseEntity<ErrorResponseDto> resposta, int status, String codigo, String retryAfter) {
        assertThat(resposta.getStatusCode().value()).isEqualTo(status);
        assertThat(resposta.getBody()).isNotNull();
        assertThat(resposta.getBody().status()).isEqualTo(status);
        assertThat(resposta.getBody().traceId()).isEqualTo(TRACE);
        assertThat(resposta.getBody().path()).isEqualTo(PATH);
        assertThat(resposta.getBody().codigo()).isEqualTo(codigo);
        assertThat(resposta.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo(retryAfter);

        if (codigo != null) {
            assertThat(CatalogoCodigosErro.publicados())
                    .as("todo codigo que chega ao corpo tem de estar no catalogo publicado")
                    .contains(codigo);
        }
    }
}
