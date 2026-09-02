package com.dynamis.sep_api.identity.infrastructure.security;

import com.dynamis.sep_api.shared.web.OrigemDaRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class RateLimitFilterTest {

    private RateLimitFilter filter;

    @BeforeEach
    void setup() {
        RateLimitProperties props = new RateLimitProperties();
        props.setLoginPerMinutePerIp(2);
        props.setTotpVerifyPerMinutePerIp(2);
        ObjectMapper om = new ObjectMapper().registerModule(new JavaTimeModule());
        filter = new RateLimitFilter(props, om);
    }

    private MockHttpServletRequest req(String path, String method, String ip) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI(path);
        req.setMethod(method);
        req.setRemoteAddr(ip);
        return req;
    }

    @Test
    void requestForaDosCaminhosProtegidosPassa() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest req = req("/api/v1/usuarios", "GET", "1.1.1.1");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, chain);

        verify(chain).doFilter(req, res);
        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    void primeirasRequestsLoginPassam() throws Exception {
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 2; i++) {
            MockHttpServletRequest r = req("/api/v1/auth/login", "POST", "9.9.9.9");
            filter.doFilter(r, new MockHttpServletResponse(), chain);
        }

        verify(chain, times(2)).doFilter(any(), any());
    }

    @Test
    void excederLimiteLoginRetorna429() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        for (int i = 0; i < 2; i++) {
            filter.doFilter(req("/api/v1/auth/login", "POST", "8.8.8.8"), new MockHttpServletResponse(), chain);
        }

        MockHttpServletResponse extraRes = new MockHttpServletResponse();
        filter.doFilter(req("/api/v1/auth/login", "POST", "8.8.8.8"), extraRes, chain);

        assertThat(extraRes.getStatus()).isEqualTo(429);
        assertThat(extraRes.getContentAsString()).contains("Too Many Requests");
    }

    @Test
    void excederLimiteTotpVerifyRetorna429() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        for (int i = 0; i < 2; i++) {
            filter.doFilter(req("/api/v1/auth/totp/verify", "POST", "7.7.7.7"), new MockHttpServletResponse(), chain);
        }

        MockHttpServletResponse extraRes = new MockHttpServletResponse();
        filter.doFilter(req("/api/v1/auth/totp/verify", "POST", "7.7.7.7"), extraRes, chain);

        assertThat(extraRes.getStatus()).isEqualTo(429);
    }

    /**
     * Sprint 34 Task 34.3: o {@code 429} tambem diz quando voltar. O valor e o periodo de refresh do
     * limitador — pior caso, porque o Resilience4j so daria o tempo exato por
     * {@code reservePermission()}, que consome uma reserva.
     */
    @Test
    void resposta429TrazRetryAfterDoPeriodoDeRefresh() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        for (int i = 0; i < 2; i++) {
            filter.doFilter(req("/api/v1/auth/login", "POST", "6.6.6.6"), new MockHttpServletResponse(), chain);
        }

        MockHttpServletResponse extraRes = new MockHttpServletResponse();
        filter.doFilter(req("/api/v1/auth/login", "POST", "6.6.6.6"), extraRes, chain);

        assertThat(extraRes.getStatus()).isEqualTo(429);
        assertThat(extraRes.getHeader(HttpHeaders.RETRY_AFTER)).isEqualTo("60");
    }

    /**
     * Sprint 34 Task 34.4: o mapa de limitadores para de crescer sem limite. Ate a Sprint 33 era um
     * {@code RateLimiterRegistry} com get-or-create por IP e sem teto — e como o {@code extrairIp}
     * da epoca confiava no {@code X-Forwarded-For} sem allowlist (fechado na Sprint 35 Task 35.2),
     * quem escolhia quantas chaves criar era o cliente.
     */
    @Test
    void mapaDeLimitadoresRespeitaOTeto() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        int excedente = 100;

        for (int i = 0; i < RateLimitFilter.MAX_LIMITADORES + excedente; i++) {
            encherComIpDistinto(chain, i);
        }

        assertThat(filter.limitadoresAtivos())
                .as("sem teto o mapa teria %d entradas", RateLimitFilter.MAX_LIMITADORES + excedente)
                .isEqualTo(RateLimitFilter.MAX_LIMITADORES);
    }

    /**
     * O teto tem que evictar, nao parar de aceitar. Uma politica "mapa cheio, nao guarda mais"
     * respeita o tamanho identicamente e <b>desliga o rate limit</b>: cada request de IP novo ganha
     * um limitador descartavel com orcamento inteiro. Sem este caso a suite aprova o fail-open.
     */
    @Test
    void comOMapaCheioUmIpNovoContinuaSendoLimitado() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        for (int i = 0; i < RateLimitFilter.MAX_LIMITADORES; i++) {
            encherComIpDistinto(chain, i);
        }

        MockHttpServletResponse primeira = new MockHttpServletResponse();
        MockHttpServletResponse segunda = new MockHttpServletResponse();
        MockHttpServletResponse terceira = new MockHttpServletResponse();
        filter.doFilter(req("/api/v1/auth/login", "POST", "4.4.4.4"), primeira, chain);
        filter.doFilter(req("/api/v1/auth/login", "POST", "4.4.4.4"), segunda, chain);
        filter.doFilter(req("/api/v1/auth/login", "POST", "4.4.4.4"), terceira, chain);

        assertThat(primeira.getStatus()).isEqualTo(200);
        assertThat(segunda.getStatus()).isEqualTo(200);
        assertThat(terceira.getStatus())
                .as("o limitador de 4.4.4.4 tem que persistir entre requests mesmo com o mapa cheio")
                .isEqualTo(429);
    }

    private void encherComIpDistinto(FilterChain chain, int indice) throws Exception {
        MockHttpServletRequest r = req("/api/v1/auth/login", "POST", "203.0.113." + indice);
        filter.doFilter(r, new MockHttpServletResponse(), chain);
    }

    /**
     * A evicção nao pode devolver orcamento: a entrada descartada e a mais antiga <b>em acesso</b>,
     * entao um IP que continua batendo permanece no mapa e segue barrado mesmo depois de o teto
     * estourar muitas vezes.
     *
     * <p>E este o caso que separa LRU de FIFO. Sob ordem de <b>insercao</b>, {@code 5.5.5.5} e o
     * mais antigo inserido, seria evictado no primeiro estouro e voltaria com orcamento cheio — a
     * ultima request responderia 200. O {@code isEqualTo(MAX_LIMITADORES)} antes do desfecho existe
     * para que a distincao nao dependa do fixture: se o teto nunca estourar, o teste passaria sob
     * as duas politicas provando nada.
     */
    @Test
    void evicaoNaoRessuscitaOOrcamentoDeQuemSegueAtivo() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        for (int i = 0; i < 2; i++) {
            filter.doFilter(req("/api/v1/auth/login", "POST", "5.5.5.5"), new MockHttpServletResponse(), chain);
        }

        for (int i = 0; i < 2 * RateLimitFilter.MAX_LIMITADORES; i++) {
            encherComIpDistinto(chain, i);
            // Mantem 5.5.5.5 como recem-acessado, entao ele nunca e o eldest.
            filter.doFilter(req("/api/v1/auth/login", "POST", "5.5.5.5"), new MockHttpServletResponse(), chain);
        }

        assertThat(filter.limitadoresAtivos())
                .as("sem estourar o teto o teste nao distingue LRU de FIFO")
                .isEqualTo(RateLimitFilter.MAX_LIMITADORES);

        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req("/api/v1/auth/login", "POST", "5.5.5.5"), res, chain);

        assertThat(res.getStatus())
                .as("o limitador de 5.5.5.5 sobreviveu ao teto e continua contando")
                .isEqualTo(429);
    }

    /**
     * O teto limita entradas, nao bytes. Sem cortar por tamanho, 10.000 chaves de 8 KB valem mais de
     * 80 MB em vez dos ~4 MB anunciados, e o valor ainda estouraria o {@code VARCHAR(45)} de
     * {@code login_attempt.ip}, abortando o rastro.
     *
     * <p>O corte segue necessario depois da Sprint 35 Task 35.2, que fez {@code extrairIp} ler so o
     * {@code getRemoteAddr()}: atras de proxy confiavel quem escreve ali e o {@code RemoteIpValve},
     * com texto vindo do {@code X-Forwarded-For} — origem de rede deixou de ser o cliente, mas nao
     * virou um socket.
     */
    @Test
    void origemMaiorQueOLimiteViraBaldeUnico() {
        String logoAcimaDoLimite = "a".repeat(OrigemDaRequest.MAX_TAMANHO + 1);
        String bemMaior = "b".repeat(9000);

        assertThat(RateLimitFilter.extrairIp(req("/p", "POST", logoAcimaDoLimite)))
                .as("duas origens grandes e diferentes precisam colapsar no MESMO balde, senao o teto"
                        + " de entradas nao limita bytes")
                .isEqualTo(RateLimitFilter.extrairIp(req("/p", "POST", bemMaior)))
                .isEqualTo("unknown");
    }

    @Test
    void ipv6NoLimiteDeTamanhoEhPreservado() {
        String ipv6 = "0000:0000:0000:0000:0000:ffff:192.168.100.228";
        assertThat(ipv6.length())
                .as("o IPv6 com mapeamento IPv4 no maior tamanho possivel nao pode ser cortado")
                .isEqualTo(OrigemDaRequest.MAX_TAMANHO);

        assertThat(RateLimitFilter.extrairIp(req("/p", "POST", ipv6))).isEqualTo(ipv6);
    }

    /**
     * Sprint 35 Task 35.2 — inverte o teste anterior, que travava o comportamento vulneravel. Quem
     * decide se o {@code X-Forwarded-For} vale e o {@code RemoteIpValve}, pelo allowlist de proxy;
     * consultar o header aqui anulava esse allowlist, porque para o peer nao confiavel o valve
     * <b>ignora</b> o header mas nao o remove. {@code OrigemForaDoAllowlistIT} cobre o mesmo pelo
     * fio, com o valve de verdade.
     */
    @Test
    void extrairIpIgnoraXForwardedFor() {
        MockHttpServletRequest r = req("/p", "GET", "10.1.2.3");
        r.addHeader("X-Forwarded-For", "203.0.113.10, 10.0.0.1");

        assertThat(RateLimitFilter.extrairIp(r)).isEqualTo("10.1.2.3");
    }
}
