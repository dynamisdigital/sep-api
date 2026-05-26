package com.dynamis.sep_api.backoffice.application.port.out;

import com.dynamis.sep_api.backoffice.application.dto.InadimplenciaConsolidada;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Porta de saida (Sprint 14 Task 14.5) — expoe agregados de cobranca usados pelo dashboard.
 * Isola backoffice do dominio/repos de {@code cobranca}. Recebe intervalo
 * {@code [inicio, fim)} pra que o caller controle o timezone do "dia operacional".
 */
public interface DashboardCobrancaQueryPort {

    BigDecimal recebimentosNoIntervalo(OffsetDateTime inicio, OffsetDateTime fim);

    InadimplenciaConsolidada inadimplenciaTotal();
}
