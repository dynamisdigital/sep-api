package com.dynamis.sep_api.onboarding.domain.event;

import com.dynamis.sep_api.onboarding.domain.vo.AlvoPld;

import java.util.UUID;

/** Evento publicado quando uma consulta PLD inicia para um alvo (PF, PJ ou representante). */
public record PldIniciadoEvent(UUID solicitacaoId, AlvoPld alvoTipo, String documentoMascarado) {}
