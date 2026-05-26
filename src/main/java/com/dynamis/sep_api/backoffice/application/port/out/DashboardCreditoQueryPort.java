package com.dynamis.sep_api.backoffice.application.port.out;

import com.dynamis.sep_api.backoffice.application.dto.ContadorPorStatusProposta;

import java.util.List;

/** Porta de saida (Sprint 14 Task 14.5) — agregados de credito (contagens por status de proposta). */
public interface DashboardCreditoQueryPort {

    List<ContadorPorStatusProposta> contagemPorStatus();
}
