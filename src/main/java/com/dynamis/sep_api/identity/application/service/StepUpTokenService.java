package com.dynamis.sep_api.identity.application.service;

import com.dynamis.sep_api.identity.domain.model.StepUpToken;
import com.dynamis.sep_api.identity.infrastructure.persistence.StepUpTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Geracao e validacao de step-up tokens (Sprint 5 Task 5.6). Mesma estrategia do
 * {@code RefreshTokenService}: cru = 32 bytes Base64URL; persiste apenas SHA-256 hex.
 */
@Service
public class StepUpTokenService {

    static final long TTL_SEGUNDOS = 300L;

    private final StepUpTokenRepository repository;
    private final SecureRandom random = new SecureRandom();

    public StepUpTokenService(StepUpTokenRepository repository) {
        this.repository = repository;
    }

    public record TokenCru(String token, StepUpToken persistido) {}

    @Transactional
    public TokenCru emitir(UUID usuarioId) {
        String cru = gerarTokenCru();
        OffsetDateTime expira = OffsetDateTime.now().plusSeconds(TTL_SEGUNDOS);
        StepUpToken novo = StepUpToken.emitir(usuarioId, hashSha256Hex(cru), expira);
        return new TokenCru(cru, repository.save(novo));
    }

    /** Valida e consome step-up token. Retorna {@code usuarioId} se valido; vazio se invalido. */
    @Transactional
    public Optional<UUID> validarEConsumir(String tokenCru) {
        if (tokenCru == null || tokenCru.isBlank()) {
            return Optional.empty();
        }
        String hash = hashSha256Hex(tokenCru);
        Optional<StepUpToken> opt = repository.findByTokenHash(hash);
        if (opt.isEmpty()) {
            return Optional.empty();
        }
        StepUpToken token = opt.get();
        if (!token.estaValido()) {
            return Optional.empty();
        }
        token.marcarUsado();
        repository.save(token);
        return Optional.of(token.getUsuarioId());
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
