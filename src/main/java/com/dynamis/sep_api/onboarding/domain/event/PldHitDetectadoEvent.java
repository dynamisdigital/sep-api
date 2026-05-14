package com.dynamis.sep_api.onboarding.domain.event;

import com.dynamis.sep_api.onboarding.domain.vo.AlvoPld;
import com.dynamis.sep_api.onboarding.domain.vo.BasePld;
import com.dynamis.sep_api.onboarding.domain.vo.SeveridadePld;

import java.util.UUID;

/**
 * Evento publicado quando ao menos uma base PLD retorna hit pra um alvo. NAO carrega motivo nem
 * documento completo — detalhes sensiveis ficam em {@code consulta_pld}.
 */
public record PldHitDetectadoEvent(UUID solicitacaoId, AlvoPld alvoTipo, BasePld base, SeveridadePld severidade) {}
