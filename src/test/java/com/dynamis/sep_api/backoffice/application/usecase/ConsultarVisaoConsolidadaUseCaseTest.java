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
import com.dynamis.sep_api.backoffice.domain.vo.TipoItemFila;
import com.dynamis.sep_api.backoffice.infrastructure.persistence.ItemFilaOperacionalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConsultarVisaoConsolidadaUseCaseTest {

    private ItemFilaOperacionalRepository itemRepo;
    private DashboardCobrancaQueryPort cobranca;
    private DashboardCreditoQueryPort credito;
    private ConsultarVisaoConsolidadaUseCase useCase;

    @BeforeEach
    void setup() {
        itemRepo = mock(ItemFilaOperacionalRepository.class);
        cobranca = mock(DashboardCobrancaQueryPort.class);
        credito = mock(DashboardCreditoQueryPort.class);
        Clock clock = Clock.fixed(Instant.parse("2026-05-26T12:00:00Z"), ZoneOffset.UTC);
        BackofficeDashboardProperties props = new BackofficeDashboardProperties("America/Sao_Paulo");
        useCase = new ConsultarVisaoConsolidadaUseCase(itemRepo, cobranca, credito, props, clock);
    }

    @Test
    void dashboardCompleto_agregaDeTodasAsFontes() {
        when(itemRepo.contarPorTipo()).thenReturn(List.of(
                new ContadorPorTipo(TipoItemFila.ONBOARDING_ERRO, 10),
                new ContadorPorTipo(TipoItemFila.COBRANCA_INADIMPLENTE, 5)));
        when(itemRepo.contarPorPrioridade()).thenReturn(List.of(new ContadorPorPrioridade(PrioridadeItem.ALTA, 8)));
        when(itemRepo.contarPorStatus()).thenReturn(List.of(new ContadorPorStatus(StatusItemFila.ABERTO, 7)));
        when(itemRepo.tempoMedioResolucaoSegundosDesde(any())).thenReturn(7200.0);
        when(itemRepo.countByPrioridadeAndStatusInAndDataAberturaBefore(any(), any(), any())).thenReturn(2L);
        when(cobranca.recebimentosNoDia(any(LocalDate.class))).thenReturn(new BigDecimal("12345.67"));
        when(cobranca.inadimplenciaTotal()).thenReturn(new InadimplenciaConsolidada(new BigDecimal("9000"), 3));
        when(credito.contagemPorStatus()).thenReturn(List.of(new ContadorPorStatusProposta("EM_ANALISE", 4)));

        DashboardBackoffice d = useCase.consultar();

        assertThat(d.contadoresPorTipo()).hasSize(2);
        assertThat(d.contadoresPorPrioridade()).hasSize(1);
        assertThat(d.contadoresPorStatus()).hasSize(1);
        assertThat(d.tempoMedioResolucao30d()).isEqualTo(Duration.ofSeconds(7200));
        assertThat(d.itensCriticosAbertosMais48h()).isEqualTo(2L);
        assertThat(d.topCincoTiposMaisFrequentes()).hasSize(2);
        assertThat(d.topCincoTiposMaisFrequentes().get(0).tipo()).isEqualTo(TipoItemFila.ONBOARDING_ERRO);
        assertThat(d.recebimentosDoDia()).isEqualByComparingTo("12345.67");
        assertThat(d.inadimplenciaTotal().numeroParcelas()).isEqualTo(3);
        assertThat(d.propostasPorStatus()).hasSize(1);
        assertThat(d.geradoEm()).isNotNull();
    }

    @Test
    void falhaParcial_emCobranca_naoQuebraDashboard() {
        when(itemRepo.contarPorTipo()).thenReturn(List.of());
        when(itemRepo.contarPorPrioridade()).thenReturn(List.of());
        when(itemRepo.contarPorStatus()).thenReturn(List.of());
        when(itemRepo.tempoMedioResolucaoSegundosDesde(any())).thenReturn(null);
        when(itemRepo.countByPrioridadeAndStatusInAndDataAberturaBefore(any(), any(), any())).thenReturn(0L);
        when(cobranca.recebimentosNoDia(any(LocalDate.class))).thenThrow(new RuntimeException("DB out"));
        when(cobranca.inadimplenciaTotal()).thenThrow(new RuntimeException("DB out"));
        when(credito.contagemPorStatus()).thenReturn(List.of());

        DashboardBackoffice d = useCase.consultar();

        assertThat(d.recebimentosDoDia()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(d.inadimplenciaTotal().numeroParcelas()).isZero();
        assertThat(d.geradoEm()).isNotNull();
    }

    @Test
    void topCinco_limitaECoolDescendente() {
        when(itemRepo.contarPorTipo()).thenReturn(List.of(
                new ContadorPorTipo(TipoItemFila.OUTRO, 1),
                new ContadorPorTipo(TipoItemFila.ONBOARDING_ERRO, 100),
                new ContadorPorTipo(TipoItemFila.COBRANCA_INADIMPLENTE, 50),
                new ContadorPorTipo(TipoItemFila.PROPOSTA_PENDENTE, 30),
                new ContadorPorTipo(TipoItemFila.CONTRATO_NAO_ASSINADO, 20),
                new ContadorPorTipo(TipoItemFila.WEBHOOK_FALHOU, 10),
                new ContadorPorTipo(TipoItemFila.ONBOARDING_PENDENTE, 5)));
        when(itemRepo.contarPorPrioridade()).thenReturn(List.of());
        when(itemRepo.contarPorStatus()).thenReturn(List.of());
        when(itemRepo.tempoMedioResolucaoSegundosDesde(any())).thenReturn(0.0);
        when(itemRepo.countByPrioridadeAndStatusInAndDataAberturaBefore(any(), any(), any())).thenReturn(0L);
        when(cobranca.recebimentosNoDia(any(LocalDate.class))).thenReturn(BigDecimal.ZERO);
        when(cobranca.inadimplenciaTotal()).thenReturn(InadimplenciaConsolidada.vazia());
        when(credito.contagemPorStatus()).thenReturn(List.of());

        DashboardBackoffice d = useCase.consultar();

        assertThat(d.topCincoTiposMaisFrequentes()).hasSize(5);
        assertThat(d.topCincoTiposMaisFrequentes().get(0).tipo()).isEqualTo(TipoItemFila.ONBOARDING_ERRO);
        assertThat(d.topCincoTiposMaisFrequentes().get(4).tipo()).isEqualTo(TipoItemFila.WEBHOOK_FALHOU);
        assertThat(d.topCincoTiposMaisFrequentes().get(3).tipo()).isEqualTo(TipoItemFila.CONTRATO_NAO_ASSINADO);
    }
}
