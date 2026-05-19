package com.dynamis.sep_api.credito.application.usecase;

import com.dynamis.sep_api.credito.application.dto.ProcessarCallbackConsentimentoCommand;
import com.dynamis.sep_api.credito.domain.event.OpenFinanceAutorizadoEvent;
import com.dynamis.sep_api.credito.domain.event.OpenFinanceNegadoEvent;
import com.dynamis.sep_api.credito.domain.exception.ConsentimentoNaoEncontradoException;
import com.dynamis.sep_api.credito.domain.model.ConsentimentoOpenFinance;
import com.dynamis.sep_api.credito.domain.vo.StatusConsentimento;
import com.dynamis.sep_api.credito.infrastructure.persistence.ConsentimentoOpenFinanceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Processa callback Celcoin Open Finance: autorizacao ou negacao do consentimento (Sprint 9 Task
 * 9.3). Idempotencia/HMAC ja foram validados no webhook controller (Task 9.5) — aqui assumimos
 * payload aceito.
 *
 * <p>Idempotencia tardia: se consentimento ja esta em estado final igual ao recebido, ignora sem
 * efeito colateral. Estado conflitante (ex.: NEGADO chegando depois de AUTORIZADO) e logado mas
 * nao reverte — provider e fonte de verdade do estado externo.
 *
 * <p>{@code AUTORIZADO}: marca consentimento e publica {@link OpenFinanceAutorizadoEvent}. Listener
 * dedicado dispara {@link ConsultarMovimentacaoOpenFinanceUseCase} apos commit (transacao isolada
 * — falha de consulta nao desfaz autorizacao).
 *
 * <p>{@code NEGADO}: marca consentimento e publica {@link OpenFinanceNegadoEvent}. Score nao muda.
 */
@Service
public class ProcessarCallbackConsentimentoUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProcessarCallbackConsentimentoUseCase.class);

    private final ConsentimentoOpenFinanceRepository consentimentoRepository;
    private final ApplicationEventPublisher eventPublisher;

    public ProcessarCallbackConsentimentoUseCase(
            ConsentimentoOpenFinanceRepository consentimentoRepository, ApplicationEventPublisher eventPublisher) {
        this.consentimentoRepository = consentimentoRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void executar(ProcessarCallbackConsentimentoCommand cmd) {
        ConsentimentoOpenFinance consentimento = consentimentoRepository
                .findByIdExternoCelcoin(cmd.idExternoCelcoin())
                .orElseThrow(() -> new ConsentimentoNaoEncontradoException(cmd.idExternoCelcoin()));

        StatusConsentimento atual = consentimento.getStatus();
        StatusConsentimento alvo = cmd.autorizado() ? StatusConsentimento.AUTORIZADO : StatusConsentimento.NEGADO;

        if (atual == alvo) {
            log.info(
                    "Callback Open Finance idempotente idExterno={} status={} — sem reprocessamento",
                    cmd.idExternoCelcoin(),
                    atual);
            return;
        }
        if (atual.isFinal()) {
            // TODO Sprint futura: revogacao tardia de consentimento Open Finance (NEGADO chegando
            // pos AUTORIZADO) deve disparar fluxo de revogacao (/consents/{id}/revoke) — Open
            // Finance Brasil prevê endpoint dedicado. Por ora, log WARN preserva trilha e nao
            // reverte estado ja consolidado.
            log.warn(
                    "Callback Open Finance conflitante idExterno={} statusAtual={} alvo={} — ignorado, provider e fonte de verdade externa",
                    cmd.idExternoCelcoin(),
                    atual,
                    alvo);
            return;
        }

        if (cmd.autorizado()) {
            consentimento.autorizar();
            consentimentoRepository.save(consentimento);
            eventPublisher.publishEvent(new OpenFinanceAutorizadoEvent(
                    consentimento.getId(),
                    consentimento.getPropostaId(),
                    consentimento.getTomadorId(),
                    consentimento.getIdExternoCelcoin()));
        } else {
            consentimento.negar();
            consentimentoRepository.save(consentimento);
            eventPublisher.publishEvent(new OpenFinanceNegadoEvent(
                    consentimento.getId(), consentimento.getPropostaId(), consentimento.getTomadorId()));
        }
    }
}
