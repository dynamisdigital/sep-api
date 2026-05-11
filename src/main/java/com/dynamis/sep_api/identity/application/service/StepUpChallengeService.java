package com.dynamis.sep_api.identity.application.service;

import com.dynamis.sep_api.identity.application.exception.MfaChallengeInvalidoException;
import com.fasterxml.uuid.Generators;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Store in-memory de desafios de step-up (Sprint 5 Task 5.6). Mesma semantica do
 * {@link MfaChallengeService} mas escopo diferente: emitido por {@code IniciarStepUpUseCase} apos
 * usuario autenticado pedir reautenticacao para operacao sensivel; consumido pelo
 * {@code CompletarStepUpUseCase} apos validar TOTP.
 */
@Service
public class StepUpChallengeService {

    static final Duration TTL = Duration.ofMinutes(5);

    private final Map<UUID, Entrada> challenges = new ConcurrentHashMap<>();

    public UUID iniciar(UUID usuarioId) {
        purgar();
        UUID challengeId = Generators.timeBasedReorderedGenerator().generate();
        challenges.put(challengeId, new Entrada(usuarioId, Instant.now().plus(TTL)));
        return challengeId;
    }

    public UUID consumir(UUID challengeId) {
        if (challengeId == null) {
            throw new MfaChallengeInvalidoException();
        }
        Entrada entrada = challenges.remove(challengeId);
        if (entrada == null || entrada.expira.isBefore(Instant.now())) {
            throw new MfaChallengeInvalidoException();
        }
        return entrada.usuarioId;
    }

    private void purgar() {
        Instant agora = Instant.now();
        Iterator<Map.Entry<UUID, Entrada>> it = challenges.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().expira.isBefore(agora)) {
                it.remove();
            }
        }
    }

    private record Entrada(UUID usuarioId, Instant expira) {}
}
