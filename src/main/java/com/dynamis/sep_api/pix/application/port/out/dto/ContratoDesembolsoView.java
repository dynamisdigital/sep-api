package com.dynamis.sep_api.pix.application.port.out.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Projecao read-only do contrato necessaria para validar e montar um desembolso Pix (Sprint 20 Task
 * 20.1). Exposicao minima — o modulo {@code pix} nunca enxerga o agregado {@code Contrato} nem a
 * {@code PropostaCredito} de origem.
 *
 * @param valorDesembolso valor a desembolsar, derivado do {@code valorSolicitado} da proposta; pode
 *     vir {@code null} se a integridade contrato/proposta estiver quebrada (o caso eh tratado pelo
 *     use case da Task 20.2).
 */
public record ContratoDesembolsoView(
        UUID contratoId, UUID propostaId, UUID tomadorId, BigDecimal valorDesembolso, boolean assinado) {}
