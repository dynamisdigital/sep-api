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
        // NAO anotar com @Schema(type = "number"). O springdoc documenta este campo como string e
        // esta certo: o JacksonAutoConfiguration do Spring Boot desliga WRITE_DURATIONS_AS_TIMESTAMPS,
        // entao o fio leva ISO-8601 ("PT2H"), medido na Sprint 34 Task 34.6. A divergencia registrada
        // como lacuna de contrato e do lado do cliente, que declara number — nao daqui.
        @Schema(description = "Tempo medio de resolucao nos ultimos 30 dias, em ISO-8601 (ex.: PT2H).")
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
