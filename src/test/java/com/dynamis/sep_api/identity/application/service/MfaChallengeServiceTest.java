package com.dynamis.sep_api.identity.application.service;

import com.dynamis.sep_api.identity.application.exception.MfaChallengeInvalidoException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MfaChallengeServiceTest {

    @Test
    void iniciarECsumirDevolveUsuarioId() {
        MfaChallengeService svc = new MfaChallengeService();
        UUID usuarioId = UUID.randomUUID();

        UUID challenge = svc.iniciar(usuarioId);

        assertThat(svc.consumir(challenge)).isEqualTo(usuarioId);
    }

    @Test
    void consumirSegundaVezFalha() {
        MfaChallengeService svc = new MfaChallengeService();
        UUID challenge = svc.iniciar(UUID.randomUUID());

        svc.consumir(challenge);

        assertThatThrownBy(() -> svc.consumir(challenge)).isInstanceOf(MfaChallengeInvalidoException.class);
    }

    @Test
    void challengeDesconhecidoLanca() {
        MfaChallengeService svc = new MfaChallengeService();

        assertThatThrownBy(() -> svc.consumir(UUID.randomUUID())).isInstanceOf(MfaChallengeInvalidoException.class);
        assertThatThrownBy(() -> svc.consumir(null)).isInstanceOf(MfaChallengeInvalidoException.class);
    }

    @Test
    void devolverPermiteReuso() {
        MfaChallengeService svc = new MfaChallengeService();
        UUID usuarioId = UUID.randomUUID();
        UUID challenge = svc.iniciar(usuarioId);

        UUID extraido = svc.consumir(challenge);
        svc.devolver(challenge, extraido);

        assertThat(svc.consumir(challenge)).isEqualTo(usuarioId);
    }
}
