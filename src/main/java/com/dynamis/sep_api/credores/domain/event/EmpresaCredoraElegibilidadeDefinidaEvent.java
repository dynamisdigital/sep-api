package com.dynamis.sep_api.credores.domain.event;

import com.dynamis.sep_api.credores.domain.vo.StatusElegibilidade;

import java.util.UUID;

/**
 * Evento publicado quando a decisao de elegibilidade de uma credora e definida (Sprint 16): apenas
 * para resultados ELEGIVEL ou INELEGIVEL — PENDENTE nao gera evento.
 */
public record EmpresaCredoraElegibilidadeDefinidaEvent(
        UUID empresaCredoraId, UUID usuarioId, StatusElegibilidade elegibilidade, String motivo) {}
