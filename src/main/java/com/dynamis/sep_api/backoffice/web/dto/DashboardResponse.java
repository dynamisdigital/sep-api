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
        // O springdoc documenta java.time.Duration como string, mas WRITE_DURATIONS_AS_TIMESTAMPS
        // nao e desligado em lugar nenhum, entao o Jackson serializa numero — e o frontend declara
        // frontendType: number. Corrige-se a documentacao, e nao a serializacao: desligar a flag
        // passaria a emitir ISO-8601 e quebraria o cliente (Sprint 34 Task 34.6).
        @Schema(type = "number", description = "Tempo medio de resolucao nos ultimos 30 dias, em segundos.")
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
