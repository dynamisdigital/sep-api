package com.dynamis.sep_api.cobranca.web.dto;

import com.dynamis.sep_api.cobranca.domain.model.EventoCobranca;
import com.dynamis.sep_api.cobranca.domain.vo.CanalNotificacao;
import com.dynamis.sep_api.cobranca.domain.vo.StatusEventoCobranca;
import com.dynamis.sep_api.cobranca.domain.vo.TipoEventoCobranca;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "Evento operacional de cobranca (notificacao, contato, mudanca de estado).")
public record EventoCobrancaResponse(
        UUID id,
        UUID parcelaId,
        TipoEventoCobranca tipo,
        CanalNotificacao canal,
        String template,
        StatusEventoCobranca status,
        Integer diasAtraso,
        String descricao,
        UUID registradoPor,
        OffsetDateTime dataEvento) {

    public static EventoCobrancaResponse from(EventoCobranca e) {
        return new EventoCobrancaResponse(
                e.getId(),
                e.getParcelaId(),
                e.getTipo(),
                e.getCanal(),
                e.getTemplate(),
                e.getStatus(),
                e.getDiasAtraso(),
                e.getDescricao(),
                e.getRegistradoPor(),
                e.getDataEvento());
    }
}
