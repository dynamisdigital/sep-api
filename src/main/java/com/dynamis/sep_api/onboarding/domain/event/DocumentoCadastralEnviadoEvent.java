package com.dynamis.sep_api.onboarding.domain.event;

import com.dynamis.sep_api.onboarding.domain.vo.TipoDocumento;

import java.util.UUID;

/** Evento publicado quando um documento cadastral e anexado a solicitacao. */
public record DocumentoCadastralEnviadoEvent(
        UUID solicitacaoId, UUID usuarioId, UUID documentoId, TipoDocumento tipo, String sha256) {}
