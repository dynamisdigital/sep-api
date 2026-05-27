package com.dynamis.sep_api.backoffice.application.port.out;

import com.dynamis.sep_api.backoffice.application.port.out.dto.PropostaPendenciaView;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Porta de saida que isola o modulo {@code backoffice} do dominio/repositories de {@code credito}.
 * O adapter concreto delega ao repository do dono (Sprint 14 Task 14.2 — fix review manual).
 */
public interface PendenciaCreditoQueryPort {

    List<PropostaPendenciaView> propostasParadasEmAnalise(OffsetDateTime corte);
}
