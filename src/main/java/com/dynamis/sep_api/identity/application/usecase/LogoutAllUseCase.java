package com.dynamis.sep_api.identity.application.usecase;

import com.dynamis.sep_api.identity.infrastructure.persistence.RefreshTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Revoga todos os refresh tokens ativos do usuario (Sprint 5 Task 5.3). Use case: usuario suspeita
 * de comprometimento.
 */
@Service
public class LogoutAllUseCase {

    private final RefreshTokenRepository repository;

    public LogoutAllUseCase(RefreshTokenRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public int executar(UUID usuarioId) {
        return repository.revogarTodosDoUsuario(usuarioId, OffsetDateTime.now());
    }
}
