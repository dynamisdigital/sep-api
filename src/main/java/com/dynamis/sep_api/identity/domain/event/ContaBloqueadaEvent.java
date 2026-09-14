package com.dynamis.sep_api.identity.domain.event;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Conta entrou em lockout (Sprint 38, ADR 0021 §1). Publicado na transicao, dentro da transacao que
 * grava o audit {@code LOCKOUT}; o modulo de notificacao o consome depois do commit.
 *
 * <p>{@code bloqueadaEm} e o instante da falha que fechou a janela, e identifica o bloqueio:
 * reavaliar o mesmo bloqueio repete o instante, e um bloqueio posterior tem outro. O {@code username}
 * e o endereco de e-mail e so trafega em memoria.
 */
public record ContaBloqueadaEvent(UUID usuarioId, String username, OffsetDateTime bloqueadaEm, int lockoutMinutes) {

    public ContaBloqueadaEvent {
        Objects.requireNonNull(usuarioId, "usuarioId obrigatorio");
        Objects.requireNonNull(username, "username obrigatorio");
        Objects.requireNonNull(bloqueadaEm, "bloqueadaEm obrigatorio");
    }
}
