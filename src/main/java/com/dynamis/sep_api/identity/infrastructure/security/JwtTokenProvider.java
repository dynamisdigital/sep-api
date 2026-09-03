package com.dynamis.sep_api.identity.infrastructure.security;

import com.dynamis.sep_api.shared.integration.CorrelationIdFilter;
import com.dynamis.sep_api.usuarios.domain.model.Role;
import com.dynamis.sep_api.usuarios.domain.model.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.crypto.SecretKey;

/**
 * Emite e valida tokens JWT (HS256) para autenticacao da API SEP.
 *
 * <p>Secret lido de {@code app.jwt.secret} interpretado como Base64 quando possivel; senao usa
 * bytes UTF-8 (compatibilidade com placeholder dev). Minimo 256 bits exigido pelo JJWT.
 *
 * <p>Claims emitidas: {@code sub} (UUID v6 canonico), {@code email}, {@code roles}, {@code iat},
 * {@code exp}. Quando o usuario tiver flag {@link Usuario#isPrecisaRedefinirSenha()}, a claim {@code
 * password_reset_required=true} e adicionada (follow-up 5F-FIX-04 da Sprint 5). JJWT 0.12.x usa
 * {@code Jwts.parser().verifyWith(key)}.
 */
@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    /** Nome da claim que sinaliza sessao limitada a alterar a propria senha. */
    public static final String CLAIM_PASSWORD_RESET_REQUIRED = "password_reset_required";

    private final SecretKey secretKey;
    private final long expirationSeconds;

    public JwtTokenProvider(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-expiration-seconds:${app.jwt.expiration-seconds:900}}") long expirationSeconds) {
        this.secretKey = buildKey(secret);
        this.expirationSeconds = expirationSeconds;
    }

    private static SecretKey buildKey(String secret) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(secret);
            if (bytes.length < 32) {
                bytes = secret.getBytes(StandardCharsets.UTF_8);
            }
        } catch (IllegalArgumentException ex) {
            bytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        if (bytes.length < 32) {
            throw new IllegalStateException(
                    "app.jwt.secret precisa codificar pelo menos 256 bits (32 bytes); valor atual"
                            + " tem "
                            + bytes.length
                            + " bytes");
        }
        return Keys.hmacShaKeyFor(bytes);
    }

    public String gerarToken(Usuario usuario) {
        Instant agora = Instant.now();
        Instant expiracao = agora.plusSeconds(expirationSeconds);
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        log.debug("Emitindo JWT para usuario {} (correlationId={})", usuario.getId(), correlationId);
        var builder = Jwts.builder()
                .subject(usuario.getId().toString())
                .claim("email", usuario.getUsername())
                .claim(
                        "roles",
                        usuario.getRoles().stream()
                                .map(r -> "ROLE_" + r.name())
                                .sorted()
                                .toList())
                .issuedAt(Date.from(agora))
                .expiration(Date.from(expiracao));
        if (usuario.isPrecisaRedefinirSenha()) {
            builder.claim(CLAIM_PASSWORD_RESET_REQUIRED, true);
        }
        return builder.signWith(secretKey, Jwts.SIG.HS256).compact();
    }

    public boolean tokenValido(String token) {
        try {
            Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token);
            return true;
        } catch (Exception ex) {
            String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
            log.debug(
                    "Token JWT rejeitado: {} (correlationId={})", ex.getClass().getSimpleName(), correlationId);
            return false;
        }
    }

    public UsuarioAutenticado extrairPrincipal(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        UUID id = UUID.fromString(claims.getSubject());
        String email = claims.get("email", String.class);
        Set<Role> roles = extrairRoles(claims);
        Boolean resetRequired = claims.get(CLAIM_PASSWORD_RESET_REQUIRED, Boolean.class);
        return new UsuarioAutenticado(id, email, roles, Boolean.TRUE.equals(resetRequired));
    }

    private static Set<Role> extrairRoles(Claims claims) {
        Object roles = claims.get("roles");
        if (roles instanceof List<?> lista && !lista.isEmpty()) {
            Set<Role> resultado = EnumSet.noneOf(Role.class);
            for (Object item : lista) {
                String valor = item.toString();
                String semPrefixo = valor.startsWith("ROLE_") ? valor.substring(5) : valor;
                resultado.add(Role.valueOf(semPrefixo));
            }
            return resultado;
        }
        throw new IllegalArgumentException("Claim 'roles' ausente ou vazia no token");
    }

    public long getExpirationSeconds() {
        return expirationSeconds;
    }
}
