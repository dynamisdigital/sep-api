package com.dynamis.sep_api.backoffice.domain.event;

import java.util.UUID;

/**
 * Publicado pelo {@code RegistrarComentarioUseCase} (Sprint 14 Task 14.3). Consumido por audit
 * (Task 14.8). O {@code conteudoResumido} carrega no maximo 80 caracteres pra a trilha; o conteudo
 * integral fica em {@code comentario_interno}.
 */
public record ComentarioRegistradoEvent(UUID itemId, UUID comentarioId, UUID autorId, String conteudoResumido) {}
