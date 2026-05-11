package com.dynamis.sep_api.identity.application.usecase;

import com.dynamis.sep_api.identity.infrastructure.persistence.RefreshTokenRepository;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LogoutAllUseCaseTest {

    @Test
    void revogaTodosOsTokensDoUsuario() {
        RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
        when(repository.revogarTodosDoUsuario(any(), any())).thenReturn(3);
        LogoutAllUseCase useCase = new LogoutAllUseCase(repository);
        UUID usuarioId = UUID.randomUUID();

        int afetados = useCase.executar(usuarioId);

        assertThat(afetados).isEqualTo(3);
        verify(repository).revogarTodosDoUsuario(eq(usuarioId), any(OffsetDateTime.class));
    }
}
