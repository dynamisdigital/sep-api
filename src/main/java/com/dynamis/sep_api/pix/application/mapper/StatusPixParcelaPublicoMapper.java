package com.dynamis.sep_api.pix.application.mapper;

import com.dynamis.sep_api.pix.application.dto.PixPagamentoParcelaResult;
import com.dynamis.sep_api.pix.domain.model.PixRecebimento;
import com.dynamis.sep_api.pix.domain.model.PixReferenciaRecebimento;
import com.dynamis.sep_api.pix.domain.vo.StatusPixParcelaPublico;
import com.dynamis.sep_api.pix.domain.vo.StatusPixRecebimento;
import com.dynamis.sep_api.pix.domain.vo.StatusPixReferenciaRecebimento;

import java.time.OffsetDateTime;

/**
 * Deriva o status Pix publico de uma parcela (Sprint 26 — Gate P2) a partir da referencia de
 * recebimento atual e do recebimento correlacionado por {@code referenciaId}. A precedencia (primeira
 * regra que casar vence) segue a spec 026; o {@code atualizadoEm} vem da {@code dataModificacao} da
 * fonte que determinou o status vencedor. A mensagem publica e copy fixa sanitizada, presente apenas
 * nos estados de atencao ({@code DIVERGENTE}/{@code FALHOU}), nunca o {@code motivoDivergencia} bruto.
 */
public final class StatusPixParcelaPublicoMapper {

    private static final String MSG_DIVERGENTE = "Pagamento Pix em verificacao. Se persistir, procure o suporte.";
    private static final String MSG_FALHOU = "Pagamento Pix nao concluido.";

    private StatusPixParcelaPublicoMapper() {}

    public static PixPagamentoParcelaResult mapear(PixReferenciaRecebimento referencia, PixRecebimento recebimento) {
        StatusPixReferenciaRecebimento ref = referencia.getStatus();
        StatusPixRecebimento rec = recebimento == null ? null : recebimento.getStatus();

        StatusPixParcelaPublico status;
        OffsetDateTime atualizadoEm;
        if (ref == StatusPixReferenciaRecebimento.CANCELADA) {
            status = StatusPixParcelaPublico.CANCELADO;
            atualizadoEm = referencia.getDataModificacao();
        } else if (ref == StatusPixReferenciaRecebimento.EXPIRADA) {
            status = StatusPixParcelaPublico.EXPIRADO;
            atualizadoEm = referencia.getDataModificacao();
        } else if (ref == StatusPixReferenciaRecebimento.DIVERGENTE || rec == StatusPixRecebimento.NAO_IDENTIFICADO) {
            status = StatusPixParcelaPublico.DIVERGENTE;
            atualizadoEm = rec == StatusPixRecebimento.NAO_IDENTIFICADO
                    ? recebimento.getDataModificacao()
                    : referencia.getDataModificacao();
        } else if (rec == StatusPixRecebimento.FALHOU) {
            status = StatusPixParcelaPublico.FALHOU;
            atualizadoEm = recebimento.getDataModificacao();
        } else if (ref == StatusPixReferenciaRecebimento.PAGA || rec == StatusPixRecebimento.CONCILIADO) {
            status = StatusPixParcelaPublico.LIQUIDADO;
            atualizadoEm = rec == StatusPixRecebimento.CONCILIADO
                    ? recebimento.getDataModificacao()
                    : referencia.getDataModificacao();
        } else if (rec == StatusPixRecebimento.RECEBIDO || rec == StatusPixRecebimento.EM_PROCESSAMENTO) {
            status = StatusPixParcelaPublico.EM_PROCESSAMENTO;
            atualizadoEm = recebimento.getDataModificacao();
        } else {
            status = StatusPixParcelaPublico.AGUARDANDO;
            atualizadoEm = referencia.getDataModificacao();
        }
        return new PixPagamentoParcelaResult(
                status, referencia.getValorEsperado(), atualizadoEm, mensagemPublica(status));
    }

    private static String mensagemPublica(StatusPixParcelaPublico status) {
        return switch (status) {
            case DIVERGENTE -> MSG_DIVERGENTE;
            case FALHOU -> MSG_FALHOU;
            case AGUARDANDO, EM_PROCESSAMENTO, LIQUIDADO, EXPIRADO, CANCELADO -> null;
        };
    }
}
