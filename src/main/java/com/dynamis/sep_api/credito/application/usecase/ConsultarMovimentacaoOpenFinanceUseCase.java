package com.dynamis.sep_api.credito.application.usecase;

import com.dynamis.sep_api.credito.application.port.out.OpenFinanceProvider;
import com.dynamis.sep_api.credito.application.port.out.dto.MovimentacaoConsolidada;
import com.dynamis.sep_api.credito.application.service.OpenFinancePayloadSanitizer;
import com.dynamis.sep_api.credito.domain.event.OpenFinanceDadosRecebidosEvent;
import com.dynamis.sep_api.credito.domain.exception.ConsentimentoNaoAutorizadoException;
import com.dynamis.sep_api.credito.domain.exception.ConsentimentoNaoEncontradoException;
import com.dynamis.sep_api.credito.domain.model.ConsentimentoOpenFinance;
import com.dynamis.sep_api.credito.domain.model.MovimentacaoOpenFinance;
import com.dynamis.sep_api.credito.domain.vo.StatusConsentimento;
import com.dynamis.sep_api.credito.infrastructure.persistence.ConsentimentoOpenFinanceRepository;
import com.dynamis.sep_api.credito.infrastructure.persistence.MovimentacaoOpenFinanceRepository;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Consulta movimentacao Open Finance via {@code OpenFinanceProvider} e persiste snapshot
 * consolidado (Sprint 9 Task 9.3).
 *
 * <p>Pre-condicoes:
 *
 * <ul>
 *   <li>Consentimento existe (404);
 *   <li>Consentimento esta em {@link StatusConsentimento#AUTORIZADO} (422);
 * </ul>
 *
 * <p>Sanitiza payload antes de persistir ({@link OpenFinancePayloadSanitizer}) — defesa em
 * profundidade LGPD caso provider exponha dados identificaveis. Publica
 * {@link OpenFinanceDadosRecebidosEvent} — listener da Task 9.4 dispara reavaliacao.
 *
 * <p>Idempotencia: se snapshot ja existe para o consentimento (callback duplicado tardio), ignora
 * sem efeito colateral.
 */
@Service
public class ConsultarMovimentacaoOpenFinanceUseCase {

    private static final String MDC_IDEMPOTENCY_KEY = "idempotencyKey";

    private final ConsentimentoOpenFinanceRepository consentimentoRepository;
    private final MovimentacaoOpenFinanceRepository movimentacaoRepository;
    private final OpenFinanceProvider provider;
    private final OpenFinancePayloadSanitizer sanitizer;
    private final ApplicationEventPublisher eventPublisher;

    public ConsultarMovimentacaoOpenFinanceUseCase(
            ConsentimentoOpenFinanceRepository consentimentoRepository,
            MovimentacaoOpenFinanceRepository movimentacaoRepository,
            OpenFinanceProvider provider,
            OpenFinancePayloadSanitizer sanitizer,
            ApplicationEventPublisher eventPublisher) {
        this.consentimentoRepository = consentimentoRepository;
        this.movimentacaoRepository = movimentacaoRepository;
        this.provider = provider;
        this.sanitizer = sanitizer;
        this.eventPublisher = eventPublisher;
    }

    // REQUIRES_NEW: chamado por OpenFinanceAutorizadoListener AFTER_COMMIT — Hibernate session
    // do tx anterior ainda esta bound ao thread; sem REQUIRES_NEW Spring nao abre nova tx.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MovimentacaoOpenFinance executar(UUID consentimentoId) {
        ConsentimentoOpenFinance consentimento = consentimentoRepository
                .findById(consentimentoId)
                .orElseThrow(() -> new ConsentimentoNaoEncontradoException(consentimentoId.toString()));

        if (consentimento.getStatus() != StatusConsentimento.AUTORIZADO) {
            throw new ConsentimentoNaoAutorizadoException(consentimento.getStatus());
        }

        // Idempotencia: snapshot existente -> retorna sem reprocessar (callback tardio repete
        // dados). V18 unique consentimento_id garante 1:1.
        var existente = movimentacaoRepository.findByConsentimentoId(consentimentoId);
        if (existente.isPresent()) {
            return existente.get();
        }

        String correlationId = UUID.randomUUID().toString();
        String idempotencyKey = "open-finance:movement:" + consentimento.getIdExternoCelcoin();
        String mdcAnterior = MDC.get(MDC_IDEMPOTENCY_KEY);
        MDC.put(MDC_IDEMPOTENCY_KEY, idempotencyKey);
        MovimentacaoConsolidada consolidada;
        try {
            consolidada = provider.consultarMovimentacao(consentimento.getIdExternoCelcoin(), correlationId);
        } finally {
            if (mdcAnterior == null) {
                MDC.remove(MDC_IDEMPOTENCY_KEY);
            } else {
                MDC.put(MDC_IDEMPOTENCY_KEY, mdcAnterior);
            }
        }

        String payloadSanitizado = sanitizer.sanitize(consolidada.payloadConsolidado());

        // Sprint 9 fix code review Task 9.3: race condition — 2 callbacks concorrentes podem
        // passar pelo findFirstBy vazio simultaneamente. Constraint nao existe (movimentacao
        // pode ter N por consentimento por design), entao detectamos via re-check.
        MovimentacaoOpenFinance snapshot;
        try {
            snapshot = movimentacaoRepository.save(MovimentacaoOpenFinance.registrar(
                    consentimentoId,
                    consentimento.getPropostaId(),
                    payloadSanitizado,
                    consolidada.mediaEntradasMensal(),
                    consolidada.mediaSaidasMensal(),
                    consolidada.saldoMedio(),
                    consolidada.numeroMesesAvaliados()));
        } catch (DataIntegrityViolationException ex) {
            // V18 unique consentimento_id: corrida com outro callback simultaneo — retorna o
            // snapshot ja-persistido pra idempotencia ponta-a-ponta.
            return movimentacaoRepository.findByConsentimentoId(consentimentoId).orElseThrow(() -> ex);
        }

        eventPublisher.publishEvent(new OpenFinanceDadosRecebidosEvent(
                snapshot.getId(),
                consentimentoId,
                consentimento.getPropostaId(),
                consentimento.getTomadorId(),
                consolidada.numeroMesesAvaliados()));
        return snapshot;
    }
}
