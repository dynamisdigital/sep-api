package com.dynamis.sep_api.governanca.domain.event;

import java.util.UUID;

/** Evento publicado quando um parametro operacional e alterado (Sprint 18). Auditado na Task 18.6. */
public record ParametroOperacionalAlteradoEvent(
        UUID parametroId, String chave, int versao, String valorAnterior, String valorNovo, UUID atorId) {}
