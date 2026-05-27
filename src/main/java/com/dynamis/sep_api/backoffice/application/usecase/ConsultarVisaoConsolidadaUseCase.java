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
import java.time.ZoneId;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Facade GoF (Sprint 14 Task 14.5): agrega leituras de {@code backoffice}, {@code cobranca} e
 * {@code credito} em um unico snapshot. Cada agregado eh resiliente — falha em uma fonte resulta
 * em campo vazio/zero no dashboard, nao em falha total.
 *
 * <p>Janelas configuraveis (fix review manual Task 14.5):
 *
 * <ul>
 *   <li>{@code app.backoffice.dashboard.timezone} — zona do "dia operacional" de recebimentos.
 *   <li>{@code app.backoffice.dashboard.tempo-medio-janela-dias} — janela de resolucao.
 *   <li>{@code app.backoffice.dashboard.criticos-threshold-horas} — threshold de itens criticos.
 *   <li>{@code app.backoffice.dashboard.top-tipos-limit} — top N tipos mais frequentes.
 * </ul>
 */
@Service
public class ConsultarVisaoConsolidadaUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(ConsultarVisaoConsolidadaUseCase.class);

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
        OffsetDateTime corteTempoMedio = agora.minusDays(properties.tempoMedioJanelaDias());
        OffsetDateTime corteCriticos = agora.minusHours(properties.criticosThresholdHoras());

        ZoneId zone = properties.zoneId();
        LocalDate hoje = LocalDate.now(clock.withZone(zone));
        OffsetDateTime inicioDia = hoje.atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime fimDia = hoje.plusDays(1).atStartOfDay(zone).toOffsetDateTime();

        List<ContadorPorTipo> porTipo = resilienteLista("contarPorTipo", itemRepository::contarPorTipo);
        List<ContadorPorPrioridade> porPrioridade =
                resilienteLista("contarPorPrioridade", itemRepository::contarPorPrioridade);
        List<ContadorPorStatus> porStatus = resilienteLista("contarPorStatus", itemRepository::contarPorStatus);
        Duration tempoMedio = resiliente(
                "tempoMedioResolucao",
                () -> calcularTempoMedio(itemRepository.tempoMedioResolucaoSegundosDesde(corteTempoMedio)),
                Duration.ZERO);
        long criticosAbertosAntigos = resiliente(
                "itensCriticosAberto" + properties.criticosThresholdHoras() + "h",
                () -> itemRepository.countByPrioridadeAndStatusInAndDataAberturaBefore(
                        PrioridadeItem.CRITICA, ATIVOS, corteCriticos),
                0L);
        List<ContadorPorTipo> topTipos = porTipo.stream()
                .sorted(Comparator.comparingLong(ContadorPorTipo::total).reversed())
                .limit(properties.topTiposLimit())
                .toList();
        BigDecimal recebimentosHoje = resiliente(
                "recebimentosNoIntervalo",
                () -> cobrancaQuery.recebimentosNoIntervalo(inicioDia, fimDia),
                BigDecimal.ZERO);
        InadimplenciaConsolidada inadimplencia =
                resiliente("inadimplenciaTotal", cobrancaQuery::inadimplenciaTotal, InadimplenciaConsolidada.vazia());
        List<ContadorPorStatusProposta> propostasStatus =
                resilienteLista("propostasPorStatus", creditoQuery::contagemPorStatus);

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
            return resultado == null ? fallback : resultado;
        } catch (RuntimeException ex) {
            LOG.warn("dashboard {} falhou; usando fallback", aspecto, ex);
            return fallback;
        }
    }

    /**
     * Variante type-safe pra fluxos que retornam {@link List}. Sprint 15 — 15.8: substitui o cast
     * com {@code @SuppressWarnings("unchecked")} do {@link #resiliente} original.
     */
    private static <E> List<E> resilienteLista(String aspecto, Supplier<List<E>> supplier) {
        try {
            List<E> resultado = supplier.get();
            return resultado == null ? List.of() : Collections.unmodifiableList(resultado);
        } catch (RuntimeException ex) {
            LOG.warn("dashboard {} falhou; usando fallback", aspecto, ex);
            return List.of();
        }
    }
}
