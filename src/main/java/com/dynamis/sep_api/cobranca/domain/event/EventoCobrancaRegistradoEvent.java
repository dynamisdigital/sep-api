package com.dynamis.sep_api.cobranca.domain.event;

import com.dynamis.sep_api.cobranca.domain.vo.CanalNotificacao;
import com.dynamis.sep_api.cobranca.domain.vo.StatusEventoCobranca;
import com.dynamis.sep_api.cobranca.domain.vo.TipoEventoCobranca;

import java.util.UUID;

/**
 * Disparado apos persistir um {@code EventoCobranca} (Sprint 13). Consumido pela auditoria
 * reforcada (Task 13.8) e pelo backoffice (Sprint 14).
 *
 * <p>Fix code review Task 13.8: carrega {@code status}/{@code canal}/{@code template} pra evitar
 * que a auditoria fique semanticamente ambigua quando o envio falhou (NOTIFICACAO_ENVIADA com
 * status FALHA descreve uma tentativa que nao chegou ao tomador).
 */
public record EventoCobrancaRegistradoEvent(
        UUID eventoId,
        UUID parcelaId,
        TipoEventoCobranca tipo,
        StatusEventoCobranca status,
        CanalNotificacao canal,
        String template,
        Integer diasAtraso,
        UUID registradoPor) {

    public boolean foiFalha() {
        return status == StatusEventoCobranca.FALHA;
    }
}
