package com.dynamis.sep_api.credito.application.usecase;

import com.dynamis.sep_api.credito.application.service.MotorRegrasCredito;
import com.dynamis.sep_api.credito.application.service.dto.ContextoAvaliacaoCredito;
import com.dynamis.sep_api.credito.application.service.dto.RegraResultado;
import com.dynamis.sep_api.credito.application.service.dto.ResultadoAvaliacaoCredito;
import com.dynamis.sep_api.credito.domain.event.OpenFinanceReavaliacaoEvent;
import com.dynamis.sep_api.credito.domain.exception.PropostaNaoEncontradaException;
import com.dynamis.sep_api.credito.domain.model.MovimentacaoOpenFinance;
import com.dynamis.sep_api.credito.domain.model.PropostaCredito;
import com.dynamis.sep_api.credito.domain.model.RegraCreditoAvaliada;
import com.dynamis.sep_api.credito.domain.model.ScoreInterno;
import com.dynamis.sep_api.credito.domain.vo.StatusProposta;
import com.dynamis.sep_api.credito.infrastructure.persistence.MovimentacaoOpenFinanceRepository;
import com.dynamis.sep_api.credito.infrastructure.persistence.PropostaCreditoRepository;
import com.dynamis.sep_api.credito.infrastructure.persistence.RegraCreditoAvaliadaRepository;
import com.dynamis.sep_api.credito.infrastructure.persistence.ScoreInternoRepository;
import com.dynamis.sep_api.onboarding.application.query.ConsultarOnboardingParaCreditoQuery;
import com.dynamis.sep_api.onboarding.application.query.OnboardingResumoCredito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Reavalia proposta de credito enriquecida com dados Open Finance (Sprint 9 Task 9.4).
 *
 * <p>Disparada por {@code OpenFinanceDadosRecebidosListener} apos snapshot persistido em
 * {@code movimentacao_open_finance}. Re-executa {@link MotorRegrasCredito} com {@link
 * ContextoAvaliacaoCredito} enriquecido — {@link com.dynamis.sep_api.credito.application.service.regras.RegraOpenFinanceMovimentacao}
 * adiciona bonus/penalidade ao score conforme padrao de movimentacao bancaria.
 *
 * <p>Pre-condicoes:
 *
 * <ul>
 *   <li>Proposta existe (404);
 *   <li>Status NAO e final ({@code APROVADA}/{@code REJEITADA}) — propostas ja decididas nao sao
 *       reavaliadas;
 *   <li>Snapshot Open Finance existe pra proposta (sem snapshot — log + skip).
 * </ul>
 *
 * <p>Decisao conservadora (Step 009.4.3): reavaliacao SO PROMOVE pra {@link
 * StatusProposta#PRE_APROVADA} quando score cruza threshold; NAO rejeita automaticamente mesmo
 * que score caia abaixo de {@code scoreAnalise} — parecer manual de operador {@code FINANCEIRO}
 * preserva discricionariedade quando Open Finance piora score.
 *
 * <p>Sempre publica {@link OpenFinanceReavaliacaoEvent} com comparativo score antes/depois pra
 * auditoria (Task 9.7) independente de mudanca de status.
 */
@Service
public class ReavaliarPropostaComOpenFinanceUseCase {

    private static final Logger log = LoggerFactory.getLogger(ReavaliarPropostaComOpenFinanceUseCase.class);

    private final PropostaCreditoRepository propostaRepository;
    private final MovimentacaoOpenFinanceRepository movimentacaoRepository;
    private final ScoreInternoRepository scoreRepository;
    private final RegraCreditoAvaliadaRepository regraRepository;
    private final ConsultarOnboardingParaCreditoQuery onboardingQuery;
    private final MotorRegrasCredito motor;
    private final ApplicationEventPublisher eventPublisher;

    public ReavaliarPropostaComOpenFinanceUseCase(
            PropostaCreditoRepository propostaRepository,
            MovimentacaoOpenFinanceRepository movimentacaoRepository,
            ScoreInternoRepository scoreRepository,
            RegraCreditoAvaliadaRepository regraRepository,
            ConsultarOnboardingParaCreditoQuery onboardingQuery,
            MotorRegrasCredito motor,
            ApplicationEventPublisher eventPublisher) {
        this.propostaRepository = propostaRepository;
        this.movimentacaoRepository = movimentacaoRepository;
        this.scoreRepository = scoreRepository;
        this.regraRepository = regraRepository;
        this.onboardingQuery = onboardingQuery;
        this.motor = motor;
        this.eventPublisher = eventPublisher;
    }

    // REQUIRES_NEW: chamado por OpenFinanceDadosRecebidosListener AFTER_COMMIT — sem REQUIRES_NEW
    // Spring nao abre nova tx por causa do tx synchronizer ainda bound ao thread.
    // ATENCAO: REQUIRES_NEW suspende a tx do caller — se este use case for invocado por
    // endpoint REST @Transactional no futuro, avalie se o comportamento e desejado.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<ResultadoAvaliacaoCredito> executar(UUID propostaId, UUID consentimentoId) {
        PropostaCredito proposta = propostaRepository
                .findById(propostaId)
                .orElseThrow(() -> new PropostaNaoEncontradaException(propostaId));

        if (proposta.getStatus().isFinal()) {
            log.info(
                    "Reavaliacao Open Finance ignorada para proposta {} em estado final {}",
                    propostaId,
                    proposta.getStatus());
            return Optional.empty();
        }

        MovimentacaoOpenFinance movimentacao = movimentacaoRepository
                .findFirstByPropostaIdOrderByDataRecebimentoDesc(propostaId)
                .orElse(null);
        if (movimentacao == null) {
            log.warn("Reavaliacao Open Finance sem snapshot persistido propostaId={}", propostaId);
            return Optional.empty();
        }

        OnboardingResumoCredito resumo = onboardingQuery
                .consultarPorId(proposta.getSolicitacaoOnboardingId())
                .orElseThrow(() -> new PropostaNaoEncontradaException(propostaId));

        int scoreAnterior = scoreRepository
                .findByPropostaId(propostaId)
                .map(ScoreInterno::getValor)
                .orElse(0);
        StatusProposta statusAnterior = proposta.getStatus();

        ContextoAvaliacaoCredito contexto = new ContextoAvaliacaoCredito(
                proposta,
                resumo.tipoSolicitante(),
                resumo.status(),
                resumo.dataNascimento(),
                resumo.dataAbertura(),
                movimentacao);

        ResultadoAvaliacaoCredito resultado = motor.avaliar(contexto);
        persistirNovaTrilha(propostaId, resultado);

        // Apenas promove pra PRE_APROVADA (conservador). Status piorando nao rejeita
        // automaticamente — parecer manual mantem decisao final.
        StatusProposta statusNovo = statusAnterior;
        if (statusAnterior == StatusProposta.EM_ANALISE && resultado.statusSugerido() == StatusProposta.PRE_APROVADA) {
            proposta.aplicarSugestaoMotor(StatusProposta.PRE_APROVADA);
            propostaRepository.save(proposta);
            statusNovo = StatusProposta.PRE_APROVADA;
        }

        eventPublisher.publishEvent(new OpenFinanceReavaliacaoEvent(
                propostaId,
                proposta.getTomadorId(),
                consentimentoId,
                scoreAnterior,
                resultado.score(),
                statusAnterior,
                statusNovo));

        return Optional.of(resultado);
    }

    private void persistirNovaTrilha(UUID propostaId, ResultadoAvaliacaoCredito resultado) {
        scoreRepository.deleteByPropostaId(propostaId);
        regraRepository.deleteByPropostaId(propostaId);
        // Flush explicito — score_interno_proposta_id_key (unique) colide se Hibernate
        // ordenar insert antes do delete na mesma tx. Sprint 8 PropostaAvaliacaoTransacional
        // nao sofre porque la nao ha score pre-existente; em Reavaliar SEMPRE ha.
        scoreRepository.flush();
        regraRepository.flush();
        scoreRepository.save(ScoreInterno.calculado(
                propostaId, resultado.score(), resultado.statusSugerido(), resultado.falhas(), resultado.pendencias()));
        for (RegraResultado r : resultado.regras()) {
            regraRepository.save(
                    RegraCreditoAvaliada.registrar(propostaId, r.nome(), r.resultado(), r.motivo(), r.bloqueante()));
        }
    }
}
