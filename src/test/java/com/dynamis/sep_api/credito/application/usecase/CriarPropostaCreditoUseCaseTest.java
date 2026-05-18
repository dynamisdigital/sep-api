package com.dynamis.sep_api.credito.application.usecase;

import com.dynamis.sep_api.credito.application.dto.CriarPropostaCreditoCommand;
import com.dynamis.sep_api.credito.domain.event.PropostaCriadaEvent;
import com.dynamis.sep_api.credito.domain.exception.PropostaInvalidaException;
import com.dynamis.sep_api.credito.domain.model.PropostaCredito;
import com.dynamis.sep_api.credito.domain.vo.TipoOperacao;
import com.dynamis.sep_api.credito.infrastructure.persistence.PropostaCreditoRepository;
import com.dynamis.sep_api.onboarding.application.query.ConsultarOnboardingParaCreditoQuery;
import com.dynamis.sep_api.onboarding.application.query.OnboardingResumoCredito;
import com.dynamis.sep_api.onboarding.domain.vo.StatusOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.TipoSolicitante;
import com.dynamis.sep_api.shared.exception.RecursoNaoEncontradoException;
import com.dynamis.sep_api.shared.exception.ValidacaoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CriarPropostaCreditoUseCaseTest {

    private PropostaCreditoRepository propostaRepository;
    private ConsultarOnboardingParaCreditoQuery onboardingQuery;
    private ApplicationEventPublisher eventPublisher;
    private CriarPropostaCreditoUseCase useCase;

    @BeforeEach
    void setup() {
        propostaRepository = mock(PropostaCreditoRepository.class);
        onboardingQuery = mock(ConsultarOnboardingParaCreditoQuery.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        useCase = new CriarPropostaCreditoUseCase(propostaRepository, onboardingQuery, eventPublisher);
        when(propostaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void criaPropostaQuandoOnboardingAprovadoFinal() {
        UUID tomadorId = UUID.randomUUID();
        UUID onbId = UUID.randomUUID();
        when(onboardingQuery.consultarPorId(onbId))
                .thenReturn(Optional.of(new OnboardingResumoCredito(
                        onbId,
                        tomadorId,
                        TipoSolicitante.PESSOA,
                        StatusOnboarding.APROVADO_FINAL,
                        LocalDate.of(1990, 1, 1),
                        null)));

        PropostaCredito p = useCase.executar(
                new CriarPropostaCreditoCommand(tomadorId, onbId, TipoOperacao.OUTROS, new BigDecimal("10000.00"), 12));

        assertThat(p.getTomadorId()).isEqualTo(tomadorId);
        assertThat(p.getSolicitacaoOnboardingId()).isEqualTo(onbId);
        verify(propostaRepository).save(any());
        verify(eventPublisher).publishEvent(any(PropostaCriadaEvent.class));
    }

    @Test
    void rejeitaQuandoOnboardingNaoExiste() {
        UUID onbId = UUID.randomUUID();
        when(onboardingQuery.consultarPorId(onbId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(new CriarPropostaCreditoCommand(
                        UUID.randomUUID(), onbId, TipoOperacao.OUTROS, new BigDecimal("10000"), 12)))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void rejeitaQuandoOnboardingNaoEstaAprovadoFinal() {
        UUID tomadorId = UUID.randomUUID();
        UUID onbId = UUID.randomUUID();
        when(onboardingQuery.consultarPorId(onbId))
                .thenReturn(Optional.of(new OnboardingResumoCredito(
                        onbId,
                        tomadorId,
                        TipoSolicitante.PESSOA,
                        StatusOnboarding.APROVADO,
                        LocalDate.of(1990, 1, 1),
                        null)));

        assertThatThrownBy(() -> useCase.executar(new CriarPropostaCreditoCommand(
                        tomadorId, onbId, TipoOperacao.OUTROS, new BigDecimal("10000"), 12)))
                .isInstanceOf(PropostaInvalidaException.class)
                .hasMessageContaining("APROVADO_FINAL");
    }

    @Test
    void rejeitaQuandoOnboardingPertenceAOutroTomador() {
        UUID outroDono = UUID.randomUUID();
        UUID onbId = UUID.randomUUID();
        when(onboardingQuery.consultarPorId(onbId))
                .thenReturn(Optional.of(new OnboardingResumoCredito(
                        onbId,
                        outroDono,
                        TipoSolicitante.PESSOA,
                        StatusOnboarding.APROVADO_FINAL,
                        LocalDate.of(1990, 1, 1),
                        null)));

        assertThatThrownBy(() -> useCase.executar(new CriarPropostaCreditoCommand(
                        UUID.randomUUID(), onbId, TipoOperacao.OUTROS, new BigDecimal("10000"), 12)))
                .isInstanceOf(ValidacaoException.class)
                .hasMessageContaining("outro tomador");
    }
}
