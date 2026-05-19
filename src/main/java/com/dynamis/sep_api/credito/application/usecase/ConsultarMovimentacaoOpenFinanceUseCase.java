package com.dynamis.sep_api.credito.application.usecase;

import com.dynamis.sep_api.credito.application.port.out.OpenFinanceProvider;
import com.dynamis.sep_api.credito.application.port.out.dto.MovimentacaoConsolidada;
import com.dynamis.sep_api.credito.application.service.OpenFinancePayloadSanitizer;
import com.dynamis.sep_api.credito.domain.event.OpenFinanceDadosRecebidosEvent;
import com.dynamis.sep_api.credito.domain.exception.ConsentimentoNaoEncontradoException;
import com.dynamis.sep_api.credito.domain.exception.OpenFinanceFluxoInvalidoException;
import com.dynamis.sep_api.credito.domain.model.ConsentimentoOpenFinance;
import com.dynamis.sep_api.credito.domain.model.MovimentacaoOpenFinance;
import com.dynamis.sep_api.credito.domain.vo.StatusConsentimento;
import com.dynamis.sep_api.credito.domain.vo.StatusProposta;
import com.dynamis.sep_api.credito.infrastructure.persistence.ConsentimentoOpenFinanceRepository;
import com.dynamis.sep_api.credito.infrastructure.persistence.MovimentacaoOpenFinanceRepository;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
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

    @Transactional
    public MovimentacaoOpenFinance executar(UUID consentimentoId) {
        ConsentimentoOpenFinance consentimento = consentimentoRepository
                .findById(consentimentoId)
                .orElseThrow(() -> new ConsentimentoNaoEncontradoException(consentimentoId.toString()));

        if (consentimento.getStatus() != StatusConsentimento.AUTORIZADO) {
            // Reusa exception de fluxo invalido — proposta status como proxy do estado nao autorizado
            // nao se aplica aqui; melhor usar StatusProposta.REJEITADA como sinalizacao generica.
            throw new OpenFinanceFluxoInvalidoException(StatusProposta.REJEITADA);
        }

        // Idempotencia: snapshot existente -> retorna sem reprocessar (callback tardio repete dados).
        var existente = movimentacaoRepository.findFirstByConsentimentoIdOrderByDataRecebimentoDesc(consentimentoId);
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

        MovimentacaoOpenFinance snapshot = movimentacaoRepository.save(MovimentacaoOpenFinance.registrar(
                consentimentoId,
                consentimento.getPropostaId(),
                payloadSanitizado,
                consolidada.mediaEntradasMensal(),
                consolidada.mediaSaidasMensal(),
                consolidada.saldoMedio(),
                consolidada.numeroMesesAvaliados()));

        eventPublisher.publishEvent(new OpenFinanceDadosRecebidosEvent(
                snapshot.getId(),
                consentimentoId,
                consentimento.getPropostaId(),
                consentimento.getTomadorId(),
                consolidada.numeroMesesAvaliados()));
        return snapshot;
    }
}
