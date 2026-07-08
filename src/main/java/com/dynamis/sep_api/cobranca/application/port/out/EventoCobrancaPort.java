package com.dynamis.sep_api.cobranca.application.port.out;

import com.dynamis.sep_api.cobranca.domain.model.EventoCobranca;
import com.dynamis.sep_api.cobranca.domain.vo.CanalNotificacao;

import java.util.UUID;

/**
 * Porta de saida de persistencia de {@link EventoCobranca} (Sprint 28, ADR 0007). Cobre o guard de
 * idempotencia de notificacao automatica e o registro de eventos operacionais.
 */
public interface EventoCobrancaPort {

    /** Guard de idempotencia: ja existe notificacao pra parcela no mesmo dia/canal/template? */
    boolean jaNotificado(UUID parcelaId, Integer diasAtraso, CanalNotificacao canal, String template);

    EventoCobranca salvar(EventoCobranca evento);
}
