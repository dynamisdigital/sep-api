package com.dynamis.sep_api.identity.application.usecase;

import com.dynamis.sep_api.identity.application.exception.MfaNaoHabilitadoException;
import com.dynamis.sep_api.identity.application.service.StepUpChallengeService;
import com.dynamis.sep_api.identity.domain.model.UsuarioTotpSecret;
import com.dynamis.sep_api.identity.infrastructure.persistence.UsuarioTotpSecretRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IniciarStepUpUseCaseTest {

    private UsuarioTotpSecretRepository totpRepository;
    private StepUpChallengeService challengeService;
    private IniciarStepUpUseCase useCase;

    @BeforeEach
    void setup() {
        totpRepository = mock(UsuarioTotpSecretRepository.class);
        challengeService = mock(StepUpChallengeService.class);
        useCase = new IniciarStepUpUseCase(totpRepository, challengeService);
    }

    @Test
    void emiteChallengeQuandoMfaAtivo() {
        UUID usuarioId = UUID.randomUUID();
        UsuarioTotpSecret ativo = UsuarioTotpSecret.iniciar(usuarioId, "cifrado");
        ativo.ativar();
        when(totpRepository.findByUsuarioId(usuarioId)).thenReturn(Optional.of(ativo));
        UUID challenge = UUID.randomUUID();
        when(challengeService.iniciar(usuarioId)).thenReturn(challenge);

        assertThat(useCase.executar(usuarioId)).isEqualTo(challenge);
    }

    @Test
    void semMfaLanca() {
        UUID usuarioId = UUID.randomUUID();
        when(totpRepository.findByUsuarioId(usuarioId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(usuarioId)).isInstanceOf(MfaNaoHabilitadoException.class);
    }

    @Test
    void mfaPendenteLanca() {
        UUID usuarioId = UUID.randomUUID();
        UsuarioTotpSecret pendente = UsuarioTotpSecret.iniciar(usuarioId, "cifrado");
        when(totpRepository.findByUsuarioId(usuarioId)).thenReturn(Optional.of(pendente));

        assertThatThrownBy(() -> useCase.executar(usuarioId)).isInstanceOf(MfaNaoHabilitadoException.class);
    }
}
