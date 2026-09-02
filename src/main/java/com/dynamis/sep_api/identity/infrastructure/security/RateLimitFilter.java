package com.dynamis.sep_api.identity.infrastructure.security;

import com.dynamis.sep_api.shared.exception.ErrorResponseDto;
import com.dynamis.sep_api.shared.web.OrigemDaRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Aplica rate limit por IP em rotas sensiveis de autenticacao (Sprint 5 Task 5.4): {@code POST
 * /api/v1/auth/login} e {@code POST /api/v1/auth/totp/verify}.
 *
 * <p>Um {@link RateLimiter} do Resilience4j por chave dinamica ({@code "login:" + ip} ou
 * {@code "totp-verify:" + ip}), com config especifica por endpoint. Ao exceder, devolve
 * {@code 429 Too Many Requests} com {@link ErrorResponseDto} JSON e nao chama a chain.
 *
 * <p><b>Evicção</b> (Sprint 34 Task 34.4): ate a Sprint 33 os limitadores viviam num
 * {@code RateLimiterRegistry.ofDefaults()} com get-or-create por IP, <b>sem TTL nem teto</b>, e a
 * cardinalidade das chaves era escolhida pelo cliente (ver {@link #extrairIp}, fechado na Sprint 35
 * Task 35.2): um atacante variando a origem enchia a heap sem nunca exceder limite nenhum. O teto
 * segue valendo — atras de proxy confiavel a chave ainda vem de texto encaminhado. O registry deu
 * lugar a um mapa LRU por <b>ordem de acesso</b>, limitado a {@link #MAX_LIMITADORES}.
 *
 * <p>LRU e nao expiracao por tempo porque aqui as duas quase coincidem sem precisar de relogio: a
 * entrada mais antiga em acesso e a que passou mais tempo sem consumir permissao, ou seja a que tem
 * mais chance de ja ter recuperado <b>todas</b> as permissoes — e um limitador cheio e
 * indistinguivel de um recem-criado, entao descarta-lo nao devolve orcamento a ninguem.
 *
 * <p>Forcar a evicção da <b>propria</b> entrada exige toca-la por ultimo entre
 * {@link #MAX_LIMITADORES} chaves distintas <b>dentro</b> de {@link #PERIODO_DE_REFRESH} — passado
 * esse tempo o limitador recarrega sozinho e a evicção nao compra nada. Isso e lucrativo apenas
 * acima de {@code MAX_LIMITADORES / PERIODO_DE_REFRESH} ≈ 167 req/s, e mesmo la e dominado: quem
 * consegue emitir 10.000 chaves distintas ja tem 10.000 orcamentos cheios de graca, sem precisar
 * evictar nada. O caminho de evicção nao acrescenta capacidade nenhuma.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    static final String LOGIN_PATH = "/api/v1/auth/login";
    static final String TOTP_VERIFY_PATH = "/api/v1/auth/totp/verify";

    /**
     * Teto de limitadores vivos. Uma instancia que veja mais de 10 mil IPs distintos dentro de uma
     * janela de refresh ja esta fora do porte deste monolito; o numero existe para tornar a memoria
     * limitada, nao para dimensionar capacidade.
     */
    static final int MAX_LIMITADORES = 10_000;

    /** Comprimento maximo de um IPv6 com mapeamento IPv4 e zona — e o mesmo de {@code LoginAttempt.ip}. */

    /** Janela do limitador, e tambem o {@code Retry-After} do {@code 429} — ver {@link #escreverErro429}. */
    private static final Duration PERIODO_DE_REFRESH = Duration.ofMinutes(1);

    private final Map<String, RateLimiter> limitadores;
    private final ObjectMapper objectMapper;
    private final RateLimiterConfig loginConfig;
    private final RateLimiterConfig totpVerifyConfig;

    public RateLimitFilter(RateLimitProperties properties, ObjectMapper objectMapper) {
        this.limitadores = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, RateLimiter> eldest) {
                return size() > MAX_LIMITADORES;
            }
        });
        this.objectMapper = objectMapper;
        this.loginConfig = RateLimiterConfig.custom()
                .limitForPeriod(properties.getLoginPerMinutePerIp())
                .limitRefreshPeriod(PERIODO_DE_REFRESH)
                .timeoutDuration(Duration.ZERO)
                .build();
        this.totpVerifyConfig = RateLimiterConfig.custom()
                .limitForPeriod(properties.getTotpVerifyPerMinutePerIp())
                .limitRefreshPeriod(PERIODO_DE_REFRESH)
                .timeoutDuration(Duration.ZERO)
                .build();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }
        String path = request.getRequestURI();
        RateLimiterConfig config = null;
        String prefixo = null;
        if (LOGIN_PATH.equals(path)) {
            config = loginConfig;
            prefixo = "login";
        } else if (TOTP_VERIFY_PATH.equals(path)) {
            config = totpVerifyConfig;
            prefixo = "totp-verify";
        }
        if (config == null) {
            filterChain.doFilter(request, response);
            return;
        }
        String ip = extrairIp(request);
        String key = prefixo + ":" + ip;
        RateLimiterConfig configDaChave = config;
        boolean permitido = limitadores
                .computeIfAbsent(key, chave -> RateLimiter.of(chave, configDaChave))
                .acquirePermission();
        if (!permitido) {
            log.atWarn()
                    .addKeyValue("event", "rate_limit_exceeded")
                    .addKeyValue("path", path)
                    .log("Rate limit excedido");
            escreverErro429(response, path);
            return;
        }
        filterChain.doFilter(request, response);
    }

    /** Quantos limitadores estao vivos. Existe para o teste do teto observar a evicção. */
    int limitadoresAtivos() {
        return limitadores.size();
    }

    /**
     * Origem da request, usada como chave do limitador e como {@code ip} da trilha de login.
     *
     * <p><b>Le somente {@code getRemoteAddr()}</b> (Sprint 35 Task 35.2). O
     * {@code X-Forwarded-For} nao e consultado aqui: quem decide se ele vale e o
     * {@code RemoteIpValve} do Tomcat, ligado por {@code server.forward-headers-strategy: native},
     * que so processa o header quando o <b>peer da conexao</b> casa
     * {@code server.tomcat.remoteip.internal-proxies}. Vindo de proxy confiavel, o valve ja
     * escreveu a origem real do cliente no {@code getRemoteAddr()}; vindo de qualquer outro lugar,
     * o header e um campo que o cliente preencheu sozinho e e descartado.
     *
     * <p><b>Ler o header aqui anulava o allowlist</b>, e isso foi medido, nao deduzido: com
     * {@code native} e allowlist vazio, o valve deixa o header intacto para o peer nao confiavel —
     * ele so ignora o header, nao o remove — e a versao anterior deste metodo copiava o valor
     * forjado assim mesmo. Ate a Sprint 34 o javadoc daqui afirmava que fechar o bypass era
     * "mudanca de configuracao, nao de codigo"; era das duas.
     * {@code OrigemForaDoAllowlistIT} trava o caso.
     *
     * <p>O corte por tamanho vive em {@link OrigemDaRequest} e continua necessario mesmo lendo so o
     * {@code getRemoteAddr()}: atras de proxy confiavel o valor volta a ser texto encaminhado, e sem
     * corte o teto de {@link #MAX_LIMITADORES} entradas limita a quantidade de chaves mas nao os
     * bytes — um token de 8 KB infla a memoria do mapa em mais de 20x.
     */
    public static String extrairIp(HttpServletRequest request) {
        return OrigemDaRequest.normalizar(request.getRemoteAddr());
    }

    /**
     * {@code Retry-After} com o periodo de refresh (Sprint 34 Task 34.3), nao com o tempo exato ate
     * a proxima permissao: o tempo exato so existe em
     * {@code AtomicRateLimiter.getDetailedMetrics().getNanosToWait()}, do pacote {@code internal} —
     * o {@code RateLimiter.Metrics} publico expoe apenas {@code getAvailablePermissions()} e
     * {@code getNumberOfWaitingThreads()}, e nao vale amarrar o filtro a uma classe de
     * implementacao. O periodo e o pior caso a partir de qualquer instante do ciclo recusado, e o
     * header e um hint pela RFC 9110, entao errar para mais e correto.
     */
    private void escreverErro429(HttpServletResponse response, String path) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(PERIODO_DE_REFRESH.toSeconds()));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponseDto erro = ErrorResponseDto.of(
                429,
                "Too Many Requests",
                "Limite de requisicoes excedido. Aguarde antes de tentar novamente.",
                path,
                MDC.get("correlationId"));
        objectMapper.writeValue(response.getOutputStream(), erro);
    }
}
