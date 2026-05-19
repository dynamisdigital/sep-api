package com.dynamis.sep_api.credito.application.usecase;

import com.dynamis.sep_api.credito.application.dto.IniciarConsentimentoOpenFinanceCommand;
import com.dynamis.sep_api.credito.application.port.out.OpenFinanceProvider;
import com.dynamis.sep_api.credito.application.port.out.dto.RequisicaoConsentimento;
import com.dynamis.sep_api.credito.application.port.out.dto.RespostaConsentimento;
import com.dynamis.sep_api.credito.domain.event.OpenFinanceConsentimentoIniciadoEvent;
import com.dynamis.sep_api.credito.domain.exception.ConsentimentoAtivoException;
import com.dynamis.sep_api.credito.domain.exception.OpenFinanceFluxoInvalidoException;
import com.dynamis.sep_api.credito.domain.exception.OwnershipPropostaException;
import com.dynamis.sep_api.credito.domain.exception.PropostaNaoEncontradaException;
import com.dynamis.sep_api.credito.domain.model.ConsentimentoOpenFinance;
import com.dynamis.sep_api.credito.domain.model.PropostaCredito;
import com.dynamis.sep_api.credito.domain.vo.StatusConsentimento;
import com.dynamis.sep_api.credito.domain.vo.StatusProposta;
import com.dynamis.sep_api.credito.infrastructure.persistence.ConsentimentoOpenFinanceRepository;
import com.dynamis.sep_api.credito.infrastructure.persistence.PropostaCreditoRepository;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

/**
 * Inicia consentimento Open Finance numa proposta (Sprint 9 Task 9.3).
 *
 * <p>Pre-condicoes:
 *
 * <ul>
 *   <li>Proposta existe (404);
 *   <li>Tomador autenticado e dono da proposta (403);
 *   <li>Proposta esta em {@link StatusProposta#EM_ANALISE}, {@link StatusProposta#PRE_APROVADA} ou
 *       {@link StatusProposta#PENDENCIA} (422);
 *   <li>Sem consentimento {@code PENDENTE} ativo (409) — V17 unique parcial cobre tambem em corrida.
 * </ul>
 *
 * <p>Apos validar, chama {@link OpenFinanceProvider#iniciarConsentimento} (passa Idempotency-Key
 * deterministica via MDC pra cumprir contrato adapter), persiste {@link ConsentimentoOpenFinance}
 * em {@code PENDENTE} e publica {@link OpenFinanceConsentimentoIniciadoEvent}.
 */
@Service
public class IniciarConsentimentoOpenFinanceUseCase {

    private static final Set<StatusProposta> STATUS_ACEITOS =
            Set.of(StatusProposta.EM_ANALISE, StatusProposta.PRE_APROVADA, StatusProposta.PENDENCIA);

    private static final String MDC_IDEMPOTENCY_KEY = "idempotencyKey";

    private final PropostaCreditoRepository propostaRepository;
    private final ConsentimentoOpenFinanceRepository consentimentoRepository;
    private final OpenFinanceProvider provider;
    private final ApplicationEventPublisher eventPublisher;

    public IniciarConsentimentoOpenFinanceUseCase(
            PropostaCreditoRepository propostaRepository,
            ConsentimentoOpenFinanceRepository consentimentoRepository,
            OpenFinanceProvider provider,
            ApplicationEventPublisher eventPublisher) {
        this.propostaRepository = propostaRepository;
        this.consentimentoRepository = consentimentoRepository;
        this.provider = provider;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public ConsentimentoOpenFinance executar(IniciarConsentimentoOpenFinanceCommand cmd) {
        PropostaCredito proposta = propostaRepository
                .findById(cmd.propostaId())
                .orElseThrow(() -> new PropostaNaoEncontradaException(cmd.propostaId()));

        if (!proposta.getTomadorId().equals(cmd.tomadorId())) {
            throw new OwnershipPropostaException("Proposta pertence a outro tomador");
        }
        if (!STATUS_ACEITOS.contains(proposta.getStatus())) {
            throw new OpenFinanceFluxoInvalidoException(proposta.getStatus());
        }
        consentimentoRepository
                .findFirstByPropostaIdAndStatusOrderByDataInicioDesc(cmd.propostaId(), StatusConsentimento.PENDENTE)
                .ifPresent(existing -> {
                    throw new ConsentimentoAtivoException();
                });

        // Sprint 9 fix code review Task 9.3 (anti-orphan): persiste registro local PENDENTE
        // ANTES de chamar provider externo. V17 unique parcial detecta corrida via
        // DataIntegrityViolationException -> 409 sem provider ter sido chamado. Se provider
        // falhar depois, transacao rola para tras e nao deixa consentimento local nem orfao
        // externo (idempotency-key estavel = consentimento.id permite retry idempotente).
        ConsentimentoOpenFinance consentimento;
        try {
            consentimento = consentimentoRepository.save(
                    ConsentimentoOpenFinance.iniciarLocal(cmd.propostaId(), cmd.tomadorId()));
        } catch (DataIntegrityViolationException ex) {
            throw new ConsentimentoAtivoException();
        }

        // Idempotency-Key estavel por entity id — retry da mesma operacao reusa a mesma chave
        // (provider Celcoin Finansystech retorna mesmo consent_id pra duplicatas).
        String correlationId = UUID.randomUUID().toString();
        String idempotencyKey = "open-finance:consent:" + consentimento.getId();
        String mdcAnterior = MDC.get(MDC_IDEMPOTENCY_KEY);
        MDC.put(MDC_IDEMPOTENCY_KEY, idempotencyKey);
        RespostaConsentimento resposta;
        try {
            resposta = provider.iniciarConsentimento(
                    new RequisicaoConsentimento(
                            cmd.propostaId(), cmd.tomadorId(), cmd.cpfCnpjTomador(), cmd.redirectUri()),
                    correlationId);
        } finally {
            if (mdcAnterior == null) {
                MDC.remove(MDC_IDEMPOTENCY_KEY);
            } else {
                MDC.put(MDC_IDEMPOTENCY_KEY, mdcAnterior);
            }
        }

        consentimento.vincularExterno(resposta.idExterno(), resposta.urlAutorizacao(), resposta.dataExpiracao());
        consentimento = consentimentoRepository.save(consentimento);

        eventPublisher.publishEvent(new OpenFinanceConsentimentoIniciadoEvent(
                consentimento.getId(), cmd.propostaId(), cmd.tomadorId(), resposta.idExterno()));
        return consentimento;
    }
}
