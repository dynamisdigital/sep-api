package com.dynamis.sep_api.identity.application.service;

import com.dynamis.sep_api.identity.domain.model.RefreshToken;
import com.dynamis.sep_api.identity.infrastructure.persistence.RefreshTokenRepository;
import com.dynamis.sep_api.identity.infrastructure.security.JwtProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Geracao e rotacao dos refresh tokens (Sprint 5 Task 5.3).
 *
 * <p>Token cru = 32 bytes aleatorios codificados em Base64URL (sem padding). Persistencia apenas
 * via SHA-256 hex do token cru — comparacao constant-time pelo {@link MessageDigest#isEqual}. A
 * familia ({@code familyId}) e mantida entre rotacoes: cada uso emite novo token na mesma familia e
 * marca o anterior como {@code USADO}; se um {@code USADO} for re-apresentado, toda a familia e
 * revogada.
 */
@Service
public class RefreshTokenService {

    private final RefreshTokenRepository repository;
    private final JwtProperties jwtProperties;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository repository, JwtProperties jwtProperties) {
        this.repository = repository;
        this.jwtProperties = jwtProperties;
    }

    public record TokenCru(String token, RefreshToken persistido) {}

    /** Emite token novo para um login (familia nova). */
    @Transactional
    public TokenCru emitirParaNovoLogin(UUID usuarioId) {
        String cru = gerarTokenCru();
        OffsetDateTime expira = OffsetDateTime.now().plusSeconds(jwtProperties.getRefreshExpirationSeconds());
        RefreshToken novo = RefreshToken.emitirNovoLogin(usuarioId, hashSha256Hex(cru), expira);
        return new TokenCru(cru, repository.save(novo));
    }

    /** Emite token novo na mesma familia (rotacao apos /auth/refresh). */
    @Transactional
    public TokenCru rotacionar(UUID usuarioId, UUID familyId) {
        String cru = gerarTokenCru();
        OffsetDateTime expira = OffsetDateTime.now().plusSeconds(jwtProperties.getRefreshExpirationSeconds());
        RefreshToken novo = RefreshToken.emitir(usuarioId, familyId, hashSha256Hex(cru), expira);
        return new TokenCru(cru, repository.save(novo));
    }

    public String hashSha256Hex(String tokenCru) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha.digest(tokenCru.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 nao disponivel", ex);
        }
    }

    private String gerarTokenCru() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
