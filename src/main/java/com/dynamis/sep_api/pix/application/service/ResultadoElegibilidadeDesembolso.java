package com.dynamis.sep_api.pix.application.service;

import com.dynamis.sep_api.pix.application.port.out.dto.AgendaDesembolsoView;
import com.dynamis.sep_api.pix.application.port.out.dto.ContratoDesembolsoView;
import com.dynamis.sep_api.pix.application.port.out.dto.EscrowDesembolsoView;

import java.util.Objects;

/**
 * Resultado da avaliacao de elegibilidade de desembolso (Sprint 20 Task 20.1). Quando elegivel,
 * carrega as projecoes consolidadas (contrato/agenda/escrow) para o use case de solicitacao reusar
 * sem reler. Quando inelegivel, carrega apenas o {@link MotivoInelegibilidade}.
 *
 * <p>A duplicidade de transferencia por contrato NAO eh avaliada aqui: depende da coluna
 * {@code contrato_id} em {@code pix_transferencia}, introduzida apenas na Task 20.2.
 */
public record ResultadoElegibilidadeDesembolso(
        boolean elegivel,
        MotivoInelegibilidade motivo,
        ContratoDesembolsoView contrato,
        AgendaDesembolsoView agenda,
        EscrowDesembolsoView escrow) {

    public enum MotivoInelegibilidade {
        CONTRATO_NAO_ENCONTRADO,
        CONTRATO_NAO_ASSINADO,
        AGENDA_INEXISTENTE,
        ESCROW_INOPERANTE
    }

    public static ResultadoElegibilidadeDesembolso elegivel(
            ContratoDesembolsoView contrato, AgendaDesembolsoView agenda, EscrowDesembolsoView escrow) {
        return new ResultadoElegibilidadeDesembolso(
                true,
                null,
                Objects.requireNonNull(contrato, "contrato obrigatorio"),
                Objects.requireNonNull(agenda, "agenda obrigatoria"),
                Objects.requireNonNull(escrow, "escrow obrigatorio"));
    }

    public static ResultadoElegibilidadeDesembolso inelegivel(MotivoInelegibilidade motivo) {
        return new ResultadoElegibilidadeDesembolso(
                false, Objects.requireNonNull(motivo, "motivo obrigatorio"), null, null, null);
    }
}
