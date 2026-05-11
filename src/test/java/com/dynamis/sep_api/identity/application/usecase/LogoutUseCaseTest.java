package com.dynamis.sep_api.identity.application.usecase;

import com.dynamis.sep_api.identity.application.service.RefreshTokenService;
import com.dynamis.sep_api.identity.domain.model.RefreshToken;
import com.dynamis.sep_api.identity.domain.model.RefreshTokenStatus;
import com.dynamis.sep_api.identity.infrastructure.persistence.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LogoutUseCaseTest {

    private RefreshTokenRepository repository;
    private RefreshTokenService refreshTokenService;
    private LogoutUseCase useCase;

    @BeforeEach
    void setup() {
        repository = mock(RefreshTokenRepository.class);
        refreshTokenService = mock(RefreshTokenService.class);
        useCase = new LogoutUseCase(repository, refreshTokenService);
    }

    @Test
    void logoutRevogaTokenExistente() {
        UUID usuarioId = UUID.randomUUID();
        RefreshToken ativo = RefreshToken.emitirNovoLogin(
                usuarioId, "h", OffsetDateTime.now().plusDays(30));
        when(refreshTokenService.hashSha256Hex("cru")).thenReturn("h");
        when(repository.findByTokenHash("h")).thenReturn(Optional.of(ativo));

        useCase.executar("cru");

        assertThat(ativo.getStatus()).isEqualTo(RefreshTokenStatus.REVOGADO);
        verify(repository).save(ativo);
    }

    @Test
    void logoutComTokenDesconhecidoEhNoOp() {
        when(refreshTokenService.hashSha256Hex(any())).thenReturn("h");
        when(repository.findByTokenHash("h")).thenReturn(Optional.empty());

        useCase.executar("nao-existe");

        verify(repository, never()).save(any());
    }

    @Test
    void logoutComTokenVazioEhNoOp() {
        useCase.executar("");
        useCase.executar(null);

        verify(repository, never()).findByTokenHash(any());
    }
}
