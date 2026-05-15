package com.dynamis.sep_api.onboarding.domain.event;

import com.dynamis.sep_api.onboarding.domain.vo.AlvoPld;
import com.dynamis.sep_api.onboarding.domain.vo.BasePld;
import com.dynamis.sep_api.onboarding.domain.vo.SeveridadePld;

import java.util.UUID;

/**
 * Evento publicado quando uma base PLD retorna hit pra um alvo. Carrega {@code motivo} reportado
 * pela base (auditoria BACEN/PLD exige base/motivo/severidade). Documento completo, payload bruto
 * do provider e dados completos do alvo NUNCA entram aqui — ficam em {@code consulta_pld}.
 * {@code motivo} e {@code severidade} podem vir nulos quando a base nao expoe; listener trata.
 */
public record PldHitDetectadoEvent(
        UUID solicitacaoId, AlvoPld alvoTipo, BasePld base, SeveridadePld severidade, String motivo) {}
