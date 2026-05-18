package com.dynamis.sep_api.credito.application.usecase;

import com.dynamis.sep_api.credito.application.dto.RegistrarParecerCommand;
import com.dynamis.sep_api.credito.domain.event.ParecerRegistradoEvent;
import com.dynamis.sep_api.credito.domain.event.PropostaAprovadaEvent;
import com.dynamis.sep_api.credito.domain.event.PropostaRejeitadaEvent;
import com.dynamis.sep_api.credito.domain.exception.PropostaInvalidaException;
import com.dynamis.sep_api.credito.domain.exception.PropostaNaoEncontradaException;
import com.dynamis.sep_api.credito.domain.model.DecisaoCredito;
import com.dynamis.sep_api.credito.domain.model.ParecerCredito;
import com.dynamis.sep_api.credito.domain.model.PropostaCredito;
import com.dynamis.sep_api.credito.domain.model.ScoreInterno;
import com.dynamis.sep_api.credito.domain.vo.DecisaoParecer;
import com.dynamis.sep_api.credito.domain.vo.Money;
import com.dynamis.sep_api.credito.domain.vo.StatusProposta;
import com.dynamis.sep_api.credito.domain.vo.TipoOperacao;
import com.dynamis.sep_api.credito.infrastructure.persistence.DecisaoCreditoRepository;
import com.dynamis.sep_api.credito.infrastructure.persistence.ParecerCreditoRepository;
import com.dynamis.sep_api.credito.infrastructure.persistence.PropostaCreditoRepository;
import com.dynamis.sep_api.credito.infrastructure.persistence.ScoreInternoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegistrarParecerUseCaseTest {

    private PropostaCreditoRepository propostaRepository;
    private ParecerCreditoRepository parecerRepository;
    private ScoreInternoRepository scoreRepository;
    private DecisaoCreditoRepository decisaoRepository;
    private ApplicationEventPublisher eventPublisher;
    private RegistrarParecerUseCase useCase;

    @BeforeEach
    void setup() {
        propostaRepository = mock(PropostaCreditoRepository.class);
        parecerRepository = mock(ParecerCreditoRepository.class);
        scoreRepository = mock(ScoreInternoRepository.class);
        decisaoRepository = mock(DecisaoCreditoRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        useCase = new RegistrarParecerUseCase(
                propostaRepository, parecerRepository, scoreRepository, decisaoRepository, eventPublisher);
        when(parecerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(propostaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void aprovacaoFinalizaPropostaEGravaDecisaoManual() {
        PropostaCredito proposta = novaProposta();
        proposta.aplicarSugestaoMotor(StatusProposta.PRE_APROVADA);
        UUID pareceristaId = UUID.randomUUID();
        when(propostaRepository.findById(proposta.getId())).thenReturn(Optional.of(proposta));
        when(scoreRepository.findByPropostaId(proposta.getId()))
                .thenReturn(
                        Optional.of(ScoreInterno.calculado(proposta.getId(), 850, StatusProposta.PRE_APROVADA, 0, 0)));
        when(parecerRepository.countByPropostaId(proposta.getId())).thenReturn(0L);

        ParecerCredito p = useCase.executar(new RegistrarParecerCommand(
                proposta.getId(), pareceristaId, DecisaoParecer.APROVAR, "Cliente com bom historico interno"));

        assertThat(p.getDecisao()).isEqualTo(DecisaoParecer.APROVAR);
        assertThat(p.getScoreMotorSnapshot()).isEqualTo(850);
        assertThat(p.getVersao()).isEqualTo(1);
        assertThat(proposta.getStatus()).isEqualTo(StatusProposta.APROVADA);
        verify(decisaoRepository).save(any(DecisaoCredito.class));
        verify(eventPublisher).publishEvent(any(PropostaAprovadaEvent.class));
        verify(eventPublisher).publishEvent(any(ParecerRegistradoEvent.class));
    }

    @Test
    void rejeicaoFinalizaPropostaEPublicaEventoRejeitada() {
        PropostaCredito proposta = novaProposta();
        UUID pareceristaId = UUID.randomUUID();
        when(propostaRepository.findById(proposta.getId())).thenReturn(Optional.of(proposta));
        when(scoreRepository.findByPropostaId(any())).thenReturn(Optional.empty());
        when(parecerRepository.countByPropostaId(any())).thenReturn(0L);

        useCase.executar(new RegistrarParecerCommand(
                proposta.getId(), pareceristaId, DecisaoParecer.REJEITAR, "Inconsistencia em comprovantes"));

        assertThat(proposta.getStatus()).isEqualTo(StatusProposta.REJEITADA);
        verify(decisaoRepository).save(any(DecisaoCredito.class));
        verify(eventPublisher).publishEvent(any(PropostaRejeitadaEvent.class));
    }

    @Test
    void pendenciaTransicionaSemGravarDecisao() {
        PropostaCredito proposta = novaProposta();
        UUID pareceristaId = UUID.randomUUID();
        when(propostaRepository.findById(proposta.getId())).thenReturn(Optional.of(proposta));
        when(scoreRepository.findByPropostaId(any())).thenReturn(Optional.empty());
        when(parecerRepository.countByPropostaId(any())).thenReturn(0L);

        useCase.executar(new RegistrarParecerCommand(
                proposta.getId(), pareceristaId, DecisaoParecer.PENDENCIA, "Aguardando documento adicional"));

        assertThat(proposta.getStatus()).isEqualTo(StatusProposta.PENDENCIA);
        verify(decisaoRepository, never()).save(any());
        verify(eventPublisher).publishEvent(any(ParecerRegistradoEvent.class));
    }

    @Test
    void propostaNaoEncontrada404() {
        UUID propostaId = UUID.randomUUID();
        when(propostaRepository.findById(propostaId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(new RegistrarParecerCommand(
                        propostaId, UUID.randomUUID(), DecisaoParecer.APROVAR, "Justificativa valida")))
                .isInstanceOf(PropostaNaoEncontradaException.class);
    }

    @Test
    void propostaEmEstadoFinalRejeitaNovoParecer() {
        PropostaCredito proposta = novaProposta();
        proposta.registrarDecisaoManual(DecisaoParecer.REJEITAR);
        when(propostaRepository.findById(proposta.getId())).thenReturn(Optional.of(proposta));

        assertThatThrownBy(() -> useCase.executar(new RegistrarParecerCommand(
                        proposta.getId(), UUID.randomUUID(), DecisaoParecer.APROVAR, "Tentando reabrir")))
                .isInstanceOf(PropostaInvalidaException.class);
    }

    @Test
    void justificativaInvalidaPropaga() {
        PropostaCredito proposta = novaProposta();
        when(propostaRepository.findById(proposta.getId())).thenReturn(Optional.of(proposta));
        when(scoreRepository.findByPropostaId(any())).thenReturn(Optional.empty());
        when(parecerRepository.countByPropostaId(any())).thenReturn(0L);

        assertThatThrownBy(() -> useCase.executar(new RegistrarParecerCommand(
                        proposta.getId(), UUID.randomUUID(), DecisaoParecer.APROVAR, "curta")))
                .isInstanceOf(PropostaInvalidaException.class);
    }

    @Test
    void versaoIncrementaConformeContagemAnterior() {
        PropostaCredito proposta = novaProposta();
        when(propostaRepository.findById(proposta.getId())).thenReturn(Optional.of(proposta));
        when(scoreRepository.findByPropostaId(any())).thenReturn(Optional.empty());
        when(parecerRepository.countByPropostaId(proposta.getId())).thenReturn(2L);

        ParecerCredito p = useCase.executar(new RegistrarParecerCommand(
                proposta.getId(), UUID.randomUUID(), DecisaoParecer.PENDENCIA, "Aguardando informacao adicional"));

        assertThat(p.getVersao()).isEqualTo(3);
    }

    private PropostaCredito novaProposta() {
        return PropostaCredito.criar(
                UUID.randomUUID(),
                UUID.randomUUID(),
                TipoOperacao.OUTROS,
                new Money(new BigDecimal("10000"), "BRL"),
                12);
    }
}
