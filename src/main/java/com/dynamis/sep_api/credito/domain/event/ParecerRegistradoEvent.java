package com.dynamis.sep_api.credito.domain.event;

import com.dynamis.sep_api.credito.domain.vo.DecisaoParecer;

import java.util.UUID;

/**
 * Evento publicado quando operador financeiro registra um parecer (Sprint 8 Task 8.4). Consumido
 * pelo listener de auditoria (Task 8.6) — grava {@code PARECER_REGISTRADO} com score do motor +
 * decisao manual pra trilha regulatoria.
 */
public record ParecerRegistradoEvent(
        UUID propostaId,
        UUID parecerId,
        UUID pareceristaId,
        DecisaoParecer decisao,
        String justificativa,
        Integer scoreMotor) {}
