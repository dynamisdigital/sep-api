package com.dynamis.sep_api.notificacao.domain.vo;

import java.util.Objects;
import java.util.UUID;

/** Unica referencia que uma notificacao persiste e expoe: tipo do recurso e seu id (ADR 0021 §7). */
public record Referencia(TipoReferencia tipo, UUID id) {

    public Referencia {
        Objects.requireNonNull(tipo, "tipo obrigatorio");
        Objects.requireNonNull(id, "id obrigatorio");
    }
}
