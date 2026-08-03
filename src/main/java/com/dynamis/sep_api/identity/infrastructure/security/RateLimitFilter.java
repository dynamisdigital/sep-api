package com.dynamis.sep_api.identity.infrastructure.security;

import com.dynamis.sep_api.shared.exception.ErrorResponseDto;
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
 * cardinalidade das chaves e escolhida pelo cliente (ver {@link #extrairIp}): um atacante variando
 * a origem enchia a heap sem nunca exceder limite nenhum. O registry deu lugar a um mapa LRU por
 * <b>ordem de acesso</b>, limitado a {@link #MAX_LIMITADORES}.
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
    static final int MAX_TAMANHO_IP = 45;

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
     * <p><b>Nao e confiavel</b>: com {@code server.forward-headers-strategy: framework} o
     * {@code ForwardedHeaderFilter} do Spring roda antes desta cadeia, consome o
     * {@code X-Forwarded-For} e copia o primeiro token — sem allowlist de proxy — para o
     * {@code getRemoteAddr()}. O ramo do header abaixo cobre o caso sem aquele filtro; nos dois
     * caminhos quem escolhe o valor e o cliente. Fechar isso e mudanca de configuracao
     * ({@code forward-headers-strategy: native} com {@code server.tomcat.remoteip.internal-proxies}
     * restrito ao CIDR do balanceador), nao de codigo, e segue como follow-up.
     *
     * <p>O que da para fazer aqui e limitar o <b>tamanho</b>: sem isso o teto de
     * {@link #MAX_LIMITADORES} entradas nao limita bytes, porque a chave e escolhida pelo cliente —
     * um token de 8 KB infla a memoria do mapa em mais de 20x. O corte tambem protege o
     * {@code LoginAttempt.ip}, coluna {@code VARCHAR(45)}: um valor maior aborta o insert do rastro
     * dentro do {@code REQUIRES_NEW} e devolve 500 sem deixar registro. 45 e o comprimento maximo de
     * um IPv6 com mapeamento IPv4 e zona, e o mesmo da coluna. Valores acima disso caem todos no
     * mesmo balde {@code unknown} — mais estrito, nunca mais permissivo.
     */
    public static String extrairIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return normalizar(forwarded.split(",")[0].trim());
        }
        return normalizar(request.getRemoteAddr());
    }

    private static String normalizar(String origem) {
        return origem == null || origem.isBlank() || origem.length() > MAX_TAMANHO_IP ? "unknown" : origem;
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
