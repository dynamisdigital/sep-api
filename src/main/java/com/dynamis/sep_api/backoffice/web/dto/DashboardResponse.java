package com.dynamis.sep_api.backoffice.web.dto;

import com.dynamis.sep_api.backoffice.application.dto.ContadorPorPrioridade;
import com.dynamis.sep_api.backoffice.application.dto.ContadorPorStatus;
import com.dynamis.sep_api.backoffice.application.dto.ContadorPorStatusProposta;
import com.dynamis.sep_api.backoffice.application.dto.ContadorPorTipo;
import com.dynamis.sep_api.backoffice.application.dto.DashboardBackoffice;
import com.dynamis.sep_api.backoffice.application.dto.InadimplenciaConsolidada;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Schema(description = "Snapshot consolidado da operacao de backoffice.")
public record DashboardResponse(
        List<ContadorPorTipo> contadoresPorTipo,
        List<ContadorPorPrioridade> contadoresPorPrioridade,
        List<ContadorPorStatus> contadoresPorStatus,
        Duration tempoMedioResolucao30d,
        long itensCriticosAbertosMais48h,
        List<ContadorPorTipo> topCincoTiposMaisFrequentes,
        BigDecimal recebimentosDoDia,
        InadimplenciaConsolidada inadimplenciaTotal,
        List<ContadorPorStatusProposta> propostasPorStatus,
        Instant geradoEm) {

    public static DashboardResponse from(DashboardBackoffice d) {
        return new DashboardResponse(
                d.contadoresPorTipo(),
                d.contadoresPorPrioridade(),
                d.contadoresPorStatus(),
                d.tempoMedioResolucao30d(),
                d.itensCriticosAbertosMais48h(),
                d.topCincoTiposMaisFrequentes(),
                d.recebimentosDoDia(),
                d.inadimplenciaTotal(),
                d.propostasPorStatus(),
                d.geradoEm());
    }
}
