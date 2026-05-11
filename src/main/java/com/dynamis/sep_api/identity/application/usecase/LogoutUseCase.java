package com.dynamis.sep_api.identity.application.usecase;

import com.dynamis.sep_api.identity.application.service.RefreshTokenService;
import com.dynamis.sep_api.identity.domain.model.RefreshToken;
import com.dynamis.sep_api.identity.infrastructure.persistence.RefreshTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Revoga o refresh token apresentado (Sprint 5 Task 5.3). Operacao idempotente: se o token nao
 * existe ou ja esta revogado, nao falha. Access token JWT continua valido ate sua expiracao curta
 * (15 min).
 */
@Service
public class LogoutUseCase {

    private final RefreshTokenRepository repository;
    private final RefreshTokenService refreshTokenService;

    public LogoutUseCase(RefreshTokenRepository repository, RefreshTokenService refreshTokenService) {
        this.repository = repository;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public void executar(String refreshTokenCru) {
        if (refreshTokenCru == null || refreshTokenCru.isBlank()) {
            return;
        }
        String hash = refreshTokenService.hashSha256Hex(refreshTokenCru);
        Optional<RefreshToken> opt = repository.findByTokenHash(hash);
        opt.ifPresent(token -> {
            token.revogar();
            repository.save(token);
        });
    }
}
