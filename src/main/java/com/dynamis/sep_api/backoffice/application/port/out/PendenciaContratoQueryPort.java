package com.dynamis.sep_api.backoffice.application.port.out;

import com.dynamis.sep_api.backoffice.application.port.out.dto.ContratoPendenciaView;

import java.time.OffsetDateTime;
import java.util.List;

/** Porta de saida — isola backoffice do dominio de {@code contratos} (Sprint 14 Task 14.2). */
public interface PendenciaContratoQueryPort {

    List<ContratoPendenciaView> contratosAceitosSemAssinatura(OffsetDateTime corte);
}
