package com.dynamis.sep_api.credito.application.usecase;

import com.dynamis.sep_api.credito.application.service.CreditoMotorProperties;
import com.dynamis.sep_api.credito.application.service.MotorRegrasCredito;
import com.dynamis.sep_api.credito.application.service.MotorTestFixtures;
import com.dynamis.sep_api.credito.application.service.RegraCredito;
import com.dynamis.sep_api.credito.application.service.dto.ContextoAvaliacaoCredito;
import com.dynamis.sep_api.credito.application.service.dto.RegraResultado;
import com.dynamis.sep_api.credito.application.service.dto.ResultadoAvaliacaoCredito;
import com.dynamis.sep_api.credito.domain.event.OpenFinanceReavaliacaoEvent;
import com.dynamis.sep_api.credito.domain.model.MovimentacaoOpenFinance;
import com.dynamis.sep_api.credito.domain.model.PropostaCredito;
import com.dynamis.sep_api.credito.domain.model.ScoreInterno;
import com.dynamis.sep_api.credito.domain.vo.DecisaoParecer;
import com.dynamis.sep_api.credito.domain.vo.Money;
import com.dynamis.sep_api.credito.domain.vo.StatusProposta;
import com.dynamis.sep_api.credito.domain.vo.TipoOperacao;
import com.dynamis.sep_api.credito.infrastructure.persistence.MovimentacaoOpenFinanceRepository;
import com.dynamis.sep_api.credito.infrastructure.persistence.PropostaCreditoRepository;
import com.dynamis.sep_api.credito.infrastructure.persistence.RegraCreditoAvaliadaRepository;
import com.dynamis.sep_api.credito.infrastructure.persistence.ScoreInternoRepository;
import com.dynamis.sep_api.onboarding.application.query.ConsultarOnboardingParaCreditoQuery;
import com.dynamis.sep_api.onboarding.application.query.OnboardingResumoCredito;
import com.dynamis.sep_api.onboarding.domain.vo.StatusOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.TipoSolicitante;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReavaliarPropostaComOpenFinanceUseCaseTest {

    private PropostaCreditoRepository propostaRepository;
    private MovimentacaoOpenFinanceRepository movimentacaoRepository;
    private ScoreInternoRepository scoreRepository;
    private RegraCreditoAvaliadaRepository regraRepository;
    private ConsultarOnboardingParaCreditoQuery onboardingQuery;
    private MotorRegrasCredito motor;
    private ApplicationEventPublisher publisher;
    private ReavaliarPropostaComOpenFinanceUseCase useCase;

    @BeforeEach
    void setup() {
        propostaRepository = mock(PropostaCreditoRepository.class);
        movimentacaoRepository = mock(MovimentacaoOpenFinanceRepository.class);
        scoreRepository = mock(ScoreInternoRepository.class);
        regraRepository = mock(RegraCreditoAvaliadaRepository.class);
        onboardingQuery = mock(ConsultarOnboardingParaCreditoQuery.class);
        publisher = mock(ApplicationEventPublisher.class);

        // motor real — passa contexto enriquecido por test mock retornando uma regra controlada
        CreditoMotorProperties props = MotorTestFixtures.propertiesDefault();
        motor = new MotorRegrasCredito(List.of(new RegraStubBonus(200)), props);

        useCase = new ReavaliarPropostaComOpenFinanceUseCase(
                propostaRepository,
                movimentacaoRepository,
                scoreRepository,
                regraRepository,
                onboardingQuery,
                motor,
                publisher);
        when(propostaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(scoreRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(regraRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private PropostaCredito propostaEmAnalise(UUID tomador) {
        return PropostaCredito.criar(tomador, UUID.randomUUID(), TipoOperacao.CAPITAL_GIRO, Money.brl("12000"), 12);
    }

    private MovimentacaoOpenFinance snapshotAlto(UUID propostaId) {
        return MovimentacaoOpenFinance.registrar(
                UUID.randomUUID(),
                propostaId,
                "{}",
                new BigDecimal("4000.00"),
                new BigDecimal("1500"),
                new BigDecimal("500"),
                6);
    }

    private OnboardingResumoCredito resumoOnboarding(UUID propostaId) {
        return new OnboardingResumoCredito(
                propostaId,
                UUID.randomUUID(),
                TipoSolicitante.EMPRESA,
                StatusOnboarding.APROVADO_FINAL,
                null,
                LocalDate.now().minusYears(3));
    }

    @Test
    void reavaliaScoreEPromovePreAprovadaQuandoCrossThreshold() {
        UUID tomador = UUID.randomUUID();
        UUID consentimentoId = UUID.randomUUID();
        PropostaCredito p = propostaEmAnalise(tomador);
        when(propostaRepository.findById(p.getId())).thenReturn(Optional.of(p));
        when(movimentacaoRepository.findFirstByPropostaIdOrderByDataRecebimentoDesc(p.getId()))
                .thenReturn(Optional.of(snapshotAlto(p.getId())));
        when(onboardingQuery.consultarPorId(any())).thenReturn(Optional.of(resumoOnboarding(p.getId())));
        when(scoreRepository.findByPropostaId(p.getId()))
                .thenReturn(Optional.of(ScoreInterno.calculado(p.getId(), 600, StatusProposta.EM_ANALISE, 0, 1)));

        Optional<ResultadoAvaliacaoCredito> resultado = useCase.executar(p.getId(), consentimentoId);

        assertThat(resultado).isPresent();
        assertThat(p.getStatus()).isEqualTo(StatusProposta.PRE_APROVADA);
        ArgumentCaptor<OpenFinanceReavaliacaoEvent> captor = ArgumentCaptor.forClass(OpenFinanceReavaliacaoEvent.class);
        verify(publisher).publishEvent(captor.capture());
        OpenFinanceReavaliacaoEvent ev = captor.getValue();
        assertThat(ev.scoreAnterior()).isEqualTo(600);
        assertThat(ev.scoreNovo()).isEqualTo(1000); // motor stub PASSOU +200 + score inicial 1000, clamped 1000
        assertThat(ev.statusAnterior()).isEqualTo(StatusProposta.EM_ANALISE);
        assertThat(ev.statusNovo()).isEqualTo(StatusProposta.PRE_APROVADA);
    }

    @Test
    void naoReavaliaSeStatusFinal() {
        UUID tomador = UUID.randomUUID();
        PropostaCredito p = propostaEmAnalise(tomador);
        p.aplicarSugestaoMotor(StatusProposta.PRE_APROVADA);
        p.registrarDecisaoManual(DecisaoParecer.APROVAR);
        when(propostaRepository.findById(p.getId())).thenReturn(Optional.of(p));

        Optional<ResultadoAvaliacaoCredito> resultado = useCase.executar(p.getId(), UUID.randomUUID());

        assertThat(resultado).isEmpty();
        verify(movimentacaoRepository, never()).findFirstByPropostaIdOrderByDataRecebimentoDesc(any());
        verify(publisher, never()).publishEvent(any());
    }

    @Test
    void naoReavaliaSeSemSnapshotAindaPersistido() {
        UUID tomador = UUID.randomUUID();
        PropostaCredito p = propostaEmAnalise(tomador);
        when(propostaRepository.findById(p.getId())).thenReturn(Optional.of(p));
        when(movimentacaoRepository.findFirstByPropostaIdOrderByDataRecebimentoDesc(p.getId()))
                .thenReturn(Optional.empty());

        Optional<ResultadoAvaliacaoCredito> resultado = useCase.executar(p.getId(), UUID.randomUUID());

        assertThat(resultado).isEmpty();
        verify(publisher, never()).publishEvent(any());
    }

    @Test
    void naoPromoveAutomaticamenteSeStatusAtualPendencia() {
        // Pendencia exige decisao manual mesmo com score alto pos Open Finance — apenas atualiza score.
        UUID tomador = UUID.randomUUID();
        PropostaCredito p = propostaEmAnalise(tomador);
        p.marcarPendencia();
        when(propostaRepository.findById(p.getId())).thenReturn(Optional.of(p));
        when(movimentacaoRepository.findFirstByPropostaIdOrderByDataRecebimentoDesc(p.getId()))
                .thenReturn(Optional.of(snapshotAlto(p.getId())));
        when(onboardingQuery.consultarPorId(any())).thenReturn(Optional.of(resumoOnboarding(p.getId())));
        when(scoreRepository.findByPropostaId(p.getId()))
                .thenReturn(Optional.of(ScoreInterno.calculado(p.getId(), 400, StatusProposta.PENDENCIA, 0, 3)));

        useCase.executar(p.getId(), UUID.randomUUID());

        assertThat(p.getStatus()).isEqualTo(StatusProposta.PENDENCIA);
        ArgumentCaptor<OpenFinanceReavaliacaoEvent> captor = ArgumentCaptor.forClass(OpenFinanceReavaliacaoEvent.class);
        verify(publisher).publishEvent(captor.capture());
        assertThat(captor.getValue().statusAnterior()).isEqualTo(StatusProposta.PENDENCIA);
        assertThat(captor.getValue().statusNovo()).isEqualTo(StatusProposta.PENDENCIA);
    }

    /** Stub que sempre devolve PASSOU + bonus configurado, sem checar contexto. */
    private static class RegraStubBonus implements RegraCredito {
        private final int bonus;

        RegraStubBonus(int bonus) {
            this.bonus = bonus;
        }

        @Override
        public String nome() {
            return "stub-bonus";
        }

        @Override
        public RegraResultado avaliar(ContextoAvaliacaoCredito contexto) {
            return RegraResultado.passouComBonus(nome(), bonus);
        }
    }
}
