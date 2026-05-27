package com.dynamis.sep_api.backoffice.application.dto;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Visao consolidada da operacao de backoffice (Sprint 14 Task 14.5). Sem dados sensiveis —
 * apenas agregados (contadores, totais financeiros, durarcoes).
 */
public record DashboardBackoffice(
        List<ContadorPorTipo> contadoresPorTipo,
        List<ContadorPorPrioridade> contadoresPorPrioridade,
        List<ContadorPorStatus> contadoresPorStatus,
        Duration tempoMedioResolucao30d,
        long itensCriticosAbertosMais48h,
        List<ContadorPorTipo> topCincoTiposMaisFrequentes,
        BigDecimal recebimentosDoDia,
        InadimplenciaConsolidada inadimplenciaTotal,
        List<ContadorPorStatusProposta> propostasPorStatus,
        Instant geradoEm) {}
