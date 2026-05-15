package com.dynamis.sep_api.onboarding.domain.event;

import com.dynamis.sep_api.onboarding.domain.vo.AlvoPld;

import java.util.UUID;

/**
 * Evento publicado quando um alvo PLD (PF, PJ ou representante) termina limpo em todas as 4 bases
 * obrigatorias. Carrega apenas {@code documentoMascarado} para auditoria — payload bruto e detalhes
 * sensiveis ficam em {@code consulta_pld}.
 */
public record PldLimpoEvent(UUID solicitacaoId, AlvoPld alvoTipo, String documentoMascarado) {}
