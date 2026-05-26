package com.dynamis.sep_api.backoffice.application.port.out;

import com.dynamis.sep_api.backoffice.application.dto.InadimplenciaConsolidada;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Porta de saida (Sprint 14 Task 14.5) — expoe agregados de cobranca usados pelo dashboard.
 * Isola backoffice do dominio/repos de {@code cobranca}.
 */
public interface DashboardCobrancaQueryPort {

    BigDecimal recebimentosNoDia(LocalDate dia);

    InadimplenciaConsolidada inadimplenciaTotal();
}
