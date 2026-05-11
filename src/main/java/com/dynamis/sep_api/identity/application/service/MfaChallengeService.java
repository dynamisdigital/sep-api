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
 * Store in-memory de desafios MFA emitidos durante o login (Sprint 5 Task 5.3).
 *
 * <p>Quando o usuario tem MFA ATIVO, o {@code AutenticarUsuarioUseCase} valida a senha e emite um
 * {@code mfaChallengeId} valido por 5 minutos; o cliente apresenta o codigo TOTP em
 * {@code /auth/totp/verify} junto com o {@code mfaChallengeId} para concluir o login e receber
 * access + refresh tokens.
 *
 * <p>Reinicios do servidor invalidam desafios em andamento — usuario tera que refazer login. E
 * tradeoff aceito para evitar nova tabela; promovivel para Redis na fase AWS.
 */
@Service
public class MfaChallengeService {

    static final Duration TTL = Duration.ofMinutes(5);

    private final Map<UUID, Entrada> challenges = new ConcurrentHashMap<>();

    public UUID iniciar(UUID usuarioId) {
        purgar();
        UUID challengeId = Generators.timeBasedReorderedGenerator().generate();
        challenges.put(challengeId, new Entrada(usuarioId, Instant.now().plus(TTL)));
        return challengeId;
    }

    /** Valida e consome o challenge. Retorna o {@code usuarioId} associado; lanca se invalido. */
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

    /** Re-permite consumo (apos falha de TOTP, o desafio continua valido ate TTL). */
    public void devolver(UUID challengeId, UUID usuarioId) {
        challenges.put(challengeId, new Entrada(usuarioId, Instant.now().plus(TTL)));
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
