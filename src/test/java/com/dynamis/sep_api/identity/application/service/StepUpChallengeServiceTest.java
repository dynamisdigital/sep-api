package com.dynamis.sep_api.identity.application.service;

import com.dynamis.sep_api.identity.application.exception.MfaChallengeInvalidoException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StepUpChallengeServiceTest {

    @Test
    void iniciarEConsumir() {
        StepUpChallengeService svc = new StepUpChallengeService();
        UUID usuarioId = UUID.randomUUID();

        UUID challenge = svc.iniciar(usuarioId);

        assertThat(svc.consumir(challenge)).isEqualTo(usuarioId);
    }

    @Test
    void consumirSegundaVezFalha() {
        StepUpChallengeService svc = new StepUpChallengeService();
        UUID challenge = svc.iniciar(UUID.randomUUID());

        svc.consumir(challenge);

        assertThatThrownBy(() -> svc.consumir(challenge)).isInstanceOf(MfaChallengeInvalidoException.class);
    }

    @Test
    void challengeNuloLanca() {
        StepUpChallengeService svc = new StepUpChallengeService();

        assertThatThrownBy(() -> svc.consumir(null)).isInstanceOf(MfaChallengeInvalidoException.class);
    }
}
