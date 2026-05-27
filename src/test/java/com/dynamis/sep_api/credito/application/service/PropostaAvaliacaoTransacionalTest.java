package com.dynamis.sep_api.credito.application.service;

import com.dynamis.sep_api.credito.application.service.dto.RegraResultado;
import com.dynamis.sep_api.credito.application.service.dto.ResultadoAvaliacaoCredito;
import com.dynamis.sep_api.credito.domain.event.PropostaRejeitadaEvent;
import com.dynamis.sep_api.credito.domain.exception.PropostaNaoEncontradaException;
import com.dynamis.sep_api.credito.domain.model.DecisaoCredito;
import com.dynamis.sep_api.credito.domain.model.PropostaCredito;
import com.dynamis.sep_api.credito.domain.model.RegraCreditoAvaliada;
import com.dynamis.sep_api.credito.domain.model.ScoreInterno;
import com.dynamis.sep_api.credito.domain.vo.Money;
import com.dynamis.sep_api.credito.domain.vo.ResultadoRegra;
import com.dynamis.sep_api.credito.domain.vo.StatusProposta;
import com.dynamis.sep_api.credito.domain.vo.TipoOperacao;
import com.dynamis.sep_api.credito.infrastructure.persistence.DecisaoCreditoRepository;
import com.dynamis.sep_api.credito.infrastructure.persistence.PropostaCreditoRepository;
import com.dynamis.sep_api.credito.infrastructure.persistence.RegraCreditoAvaliadaRepository;
import com.dynamis.sep_api.credito.infrastructure.persistence.ScoreInternoRepository;
import com.dynamis.sep_api.onboarding.application.query.ConsultarOnboardingParaCreditoQuery;
import com.dynamis.sep_api.onboarding.application.query.OnboardingResumoCredito;
import com.dynamis.sep_api.onboarding.domain.vo.StatusOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.TipoSolicitante;
import com.dynamis.sep_api.shared.audit.AuditLogSegurancaService;
import com.dynamis.sep_api.shared.audit.TipoEventoSeguranca;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PropostaAvaliacaoTransacionalTest {

    private PropostaCreditoRepository propostaRepository;
    private ScoreInternoRepository scoreRepository;
    private RegraCreditoAvaliadaRepository regraRepository;
    private DecisaoCreditoRepository decisaoRepository;
    private ConsultarOnboardingParaCreditoQuery onboardingQuery;
    private MotorRegrasCredito motor;
    private ApplicationEventPublisher eventPublisher;
    private AuditLogSegurancaService auditLogService;
    private PropostaAvaliacaoTransacional service;

    @BeforeEach
    void setup() {
        propostaRepository = mock(PropostaCreditoRepository.class);
        scoreRepository = mock(ScoreInternoRepository.class);
        regraRepository = mock(RegraCreditoAvaliadaRepository.class);
        decisaoRepository = mock(DecisaoCreditoRepository.class);
        onboardingQuery = mock(ConsultarOnboardingParaCreditoQuery.class);
        motor = mock(MotorRegrasCredito.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        auditLogService = mock(AuditLogSegurancaService.class);
        service = new PropostaAvaliacaoTransacional(
                propostaRepository,
                scoreRepository,
                regraRepository,
                decisaoRepository,
                onboardingQuery,
                motor,
                eventPublisher,
                auditLogService,
                new ObjectMapper());
        when(propostaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void aplicaSugestaoPreAprovadaPersistindoScoreERegras() {
        PropostaCredito proposta = propostaSimples();
        when(propostaRepository.findById(proposta.getId())).thenReturn(Optional.of(proposta));
        when(onboardingQuery.consultarPorId(proposta.getSolicitacaoOnboardingId()))
                .thenReturn(Optional.of(resumoPfOk(proposta.getSolicitacaoOnboardingId(), proposta.getTomadorId())));
        when(motor.avaliar(any()))
                .thenReturn(new ResultadoAvaliacaoCredito(
                        1000,
                        StatusProposta.PRE_APROVADA,
                        0,
                        0,
                        List.of(new RegraResultado("rg1", ResultadoRegra.PASSOU, null, false, 0))));

        service.avaliar(proposta.getId());

        assertThat(proposta.getStatus()).isEqualTo(StatusProposta.PRE_APROVADA);
        verify(scoreRepository).save(any(ScoreInterno.class));
        verify(regraRepository).saveAll(org.mockito.ArgumentMatchers.<Iterable<RegraCreditoAvaliada>>any());
        verify(propostaRepository).save(proposta);
        verify(auditLogService)
                .gravar(eq(TipoEventoSeguranca.PROPOSTA_AVALIADA_MOTOR), eq(proposta.getTomadorId()), anyString());
        verify(decisaoRepository, never()).save(any());
    }

    @Test
    void falhaNaGravacaoDoAuditMotorPropagaParaTriggerPendencia() {
        PropostaCredito proposta = propostaSimples();
        when(propostaRepository.findById(proposta.getId())).thenReturn(Optional.of(proposta));
        when(onboardingQuery.consultarPorId(any()))
                .thenReturn(Optional.of(resumoPfOk(proposta.getSolicitacaoOnboardingId(), proposta.getTomadorId())));
        when(motor.avaliar(any()))
                .thenReturn(new ResultadoAvaliacaoCredito(
                        900,
                        StatusProposta.PRE_APROVADA,
                        0,
                        0,
                        List.of(new RegraResultado("rg1", ResultadoRegra.PASSOU, null, false, 0))));
        org.mockito.Mockito.doThrow(new RuntimeException("audit down"))
                .when(auditLogService)
                .gravar(eq(TipoEventoSeguranca.PROPOSTA_AVALIADA_MOTOR), any(), anyString());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.avaliar(proposta.getId()))
                .isInstanceOf(RuntimeException.class);
        // Status NAO foi aplicado — exception propagada antes do save final
        assertThat(proposta.getStatus()).isEqualTo(StatusProposta.EM_ANALISE);
    }

    @Test
    void rejeicaoPorMotorGravaDecisaoCreditoEDispatchRejeitadaEvent() {
        PropostaCredito proposta = propostaSimples();
        when(propostaRepository.findById(proposta.getId())).thenReturn(Optional.of(proposta));
        when(onboardingQuery.consultarPorId(any()))
                .thenReturn(Optional.of(resumoPfOk(proposta.getSolicitacaoOnboardingId(), proposta.getTomadorId())));
        when(motor.avaliar(any()))
                .thenReturn(new ResultadoAvaliacaoCredito(
                        100,
                        StatusProposta.REJEITADA,
                        3,
                        0,
                        List.of(new RegraResultado("rg1", ResultadoRegra.FALHOU, "x", true, 0))));

        service.avaliar(proposta.getId());

        assertThat(proposta.getStatus()).isEqualTo(StatusProposta.REJEITADA);
        verify(decisaoRepository).save(any(DecisaoCredito.class));
        verify(eventPublisher).publishEvent(any(PropostaRejeitadaEvent.class));
    }

    @Test
    void propostaInexistenteLancaNaoEncontrada() {
        UUID id = UUID.randomUUID();
        when(propostaRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.avaliar(id)).isInstanceOf(PropostaNaoEncontradaException.class);
    }

    @Test
    void moverParaPendenciaIgnoraSeJaEstaPendenciaOuFinal() {
        PropostaCredito proposta = propostaSimples();
        // proposta nasce EM_ANALISE; vamos passar pra PENDENCIA primeiro
        proposta.marcarPendencia();
        when(propostaRepository.findById(proposta.getId())).thenReturn(Optional.of(proposta));

        service.moverParaPendencia(proposta.getId());

        verify(propostaRepository, never()).save(any());
    }

    @Test
    void moverParaPendenciaTransicionaQuandoEmAnalise() {
        PropostaCredito proposta = propostaSimples();
        when(propostaRepository.findById(proposta.getId())).thenReturn(Optional.of(proposta));

        service.moverParaPendencia(proposta.getId());

        assertThat(proposta.getStatus()).isEqualTo(StatusProposta.PENDENCIA);
        verify(propostaRepository).save(proposta);
    }

    private PropostaCredito propostaSimples() {
        return PropostaCredito.criar(
                UUID.randomUUID(),
                UUID.randomUUID(),
                TipoOperacao.OUTROS,
                new Money(new BigDecimal("10000"), "BRL"),
                12);
    }

    private OnboardingResumoCredito resumoPfOk(UUID solicitacaoId, UUID usuarioId) {
        return new OnboardingResumoCredito(
                solicitacaoId,
                usuarioId,
                TipoSolicitante.PESSOA,
                StatusOnboarding.APROVADO_FINAL,
                LocalDate.of(1990, 1, 1),
                null);
    }
}
