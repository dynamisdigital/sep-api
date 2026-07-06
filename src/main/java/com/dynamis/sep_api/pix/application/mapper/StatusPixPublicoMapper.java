package com.dynamis.sep_api.pix.application.mapper;

import com.dynamis.sep_api.pix.domain.vo.StatusPixPublico;
import com.dynamis.sep_api.pix.domain.vo.StatusPixTransferencia;

/**
 * Traduz o status interno da transferencia Pix ({@link StatusPixTransferencia}) para o status
 * publico ({@link StatusPixPublico}) das leituras owner-scoped do tomador e da credora (Sprint 26 —
 * Gates P1/P3). O {@code switch} exaustivo (sem {@code default}) forca o mapeamento a acompanhar
 * qualquer novo estado interno em tempo de compilacao.
 */
public final class StatusPixPublicoMapper {

    private StatusPixPublicoMapper() {}

    public static StatusPixPublico mapear(StatusPixTransferencia status) {
        return switch (status) {
            case CRIADA, SOLICITADA, PROCESSANDO -> StatusPixPublico.EM_PROCESSAMENTO;
            case CONCLUIDA -> StatusPixPublico.LIQUIDADO;
            case FALHOU -> StatusPixPublico.FALHOU;
            case CANCELADA -> StatusPixPublico.CANCELADO;
        };
    }
}
