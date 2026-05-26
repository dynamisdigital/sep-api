package com.dynamis.sep_api.backoffice.application.usecase;

import com.dynamis.sep_api.backoffice.application.dto.ContadorPorPrioridade;
import com.dynamis.sep_api.backoffice.application.dto.ContadorPorStatus;
import com.dynamis.sep_api.backoffice.application.dto.ContadorPorStatusProposta;
import com.dynamis.sep_api.backoffice.application.dto.ContadorPorTipo;
import com.dynamis.sep_api.backoffice.application.dto.DashboardBackoffice;
import com.dynamis.sep_api.backoffice.application.dto.InadimplenciaConsolidada;
import com.dynamis.sep_api.backoffice.application.port.out.DashboardCobrancaQueryPort;
import com.dynamis.sep_api.backoffice.application.port.out.DashboardCreditoQueryPort;
import com.dynamis.sep_api.backoffice.domain.vo.PrioridadeItem;
import com.dynamis.sep_api.backoffice.domain.vo.StatusItemFila;
import com.dynamis.sep_api.backoffice.infrastructure.persistence.ItemFilaOperacionalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Facade GoF (Sprint 14 Task 14.5): agrega leituras de {@code backoffice}, {@code cobranca} e
 * {@code credito} em um unico snapshot. Cada agregado eh resiliente — falha em uma fonte resulta
 * em campo vazio/zero no dashboard, nao em falha total.
 */
@Service
public class ConsultarVisaoConsolidadaUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(ConsultarVisaoConsolidadaUseCase.class);

    private static final int TOP_TIPOS_LIMIT = 5;
    private static final int TEMPO_MEDIO_JANELA_DIAS = 30;
    private static final int CRITICOS_THRESHOLD_HORAS = 48;
    private static final Set<StatusItemFila> ATIVOS = Set.of(StatusItemFila.ABERTO, StatusItemFila.EM_TRATAMENTO);

    private final ItemFilaOperacionalRepository itemRepository;
    private final DashboardCobrancaQueryPort cobrancaQuery;
    private final DashboardCreditoQueryPort creditoQuery;
    private final BackofficeDashboardProperties properties;
    private final Clock clock;

    public ConsultarVisaoConsolidadaUseCase(
            ItemFilaOperacionalRepository itemRepository,
            DashboardCobrancaQueryPort cobrancaQuery,
            DashboardCreditoQueryPort creditoQuery,
            BackofficeDashboardProperties properties,
            Clock clock) {
        this.itemRepository = itemRepository;
        this.cobrancaQuery = cobrancaQuery;
        this.creditoQuery = creditoQuery;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DashboardBackoffice consultar() {
        OffsetDateTime agora = OffsetDateTime.now(clock);
        OffsetDateTime cortePos30d = agora.minusDays(TEMPO_MEDIO_JANELA_DIAS);
        OffsetDateTime corteCriticosAntes48h = agora.minusHours(CRITICOS_THRESHOLD_HORAS);
        LocalDate hoje = LocalDate.now(clock.withZone(properties.zoneId()));

        List<ContadorPorTipo> porTipo = resiliente("contarPorTipo", itemRepository::contarPorTipo, List.of());
        List<ContadorPorPrioridade> porPrioridade =
                resiliente("contarPorPrioridade", itemRepository::contarPorPrioridade, List.of());
        List<ContadorPorStatus> porStatus = resiliente("contarPorStatus", itemRepository::contarPorStatus, List.of());
        Duration tempoMedio = resiliente(
                "tempoMedioResolucao",
                () -> calcularTempoMedio(itemRepository.tempoMedioResolucaoSegundosDesde(cortePos30d)),
                Duration.ZERO);
        long criticosAbertosAntigos = resiliente(
                "itensCriticosAbertosMais48h",
                () -> itemRepository.countByPrioridadeAndStatusInAndDataAberturaBefore(
                        PrioridadeItem.CRITICA, ATIVOS, corteCriticosAntes48h),
                0L);
        List<ContadorPorTipo> topTipos = porTipo.stream()
                .sorted(Comparator.comparingLong(ContadorPorTipo::total).reversed())
                .limit(TOP_TIPOS_LIMIT)
                .toList();
        BigDecimal recebimentosHoje =
                resiliente("recebimentosNoDia", () -> cobrancaQuery.recebimentosNoDia(hoje), BigDecimal.ZERO);
        InadimplenciaConsolidada inadimplencia =
                resiliente("inadimplenciaTotal", cobrancaQuery::inadimplenciaTotal, InadimplenciaConsolidada.vazia());
        List<ContadorPorStatusProposta> propostasStatus =
                resiliente("propostasPorStatus", creditoQuery::contagemPorStatus, List.of());

        return new DashboardBackoffice(
                porTipo,
                porPrioridade,
                porStatus,
                tempoMedio,
                criticosAbertosAntigos,
                topTipos,
                recebimentosHoje,
                inadimplencia,
                propostasStatus,
                agora.toInstant());
    }

    private static Duration calcularTempoMedio(Double segundos) {
        if (segundos == null) {
            return Duration.ZERO;
        }
        return Duration.ofSeconds(Math.round(segundos));
    }

    private static <T> T resiliente(String aspecto, Supplier<T> supplier, T fallback) {
        try {
            T resultado = supplier.get();
            if (resultado == null) {
                return fallback;
            }
            if (fallback instanceof List<?> && resultado instanceof List<?> lista) {
                @SuppressWarnings("unchecked")
                T immut = (T) Collections.unmodifiableList(lista);
                return immut;
            }
            return resultado;
        } catch (RuntimeException ex) {
            LOG.warn("dashboard {} falhou; usando fallback", aspecto, ex);
            return fallback;
        }
    }
}
