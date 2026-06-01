package com.dynamis.sep_api.pix.application.service;

import com.dynamis.sep_api.pix.application.port.out.dto.RespostaTransferenciaPix;
import com.dynamis.sep_api.pix.domain.model.PixTransferencia;
import com.dynamis.sep_api.pix.infrastructure.persistence.PixTransferenciaRepository;
import com.dynamis.sep_api.shared.exception.ConflitoException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Fronteiras transacionais do desembolso (Sprint 20 — fix anti-orphan do code review). Bean separado
 * para que cada metodo abra a propria tx {@link Propagation#REQUIRES_NEW} (proxy AOP nao intercepta
 * self-invocation no use case).
 *
 * <p>Garantia anti-orphan: {@link #inserirCriada} <strong>comita</strong> a transferencia
 * {@code CRIADA} ANTES da chamada externa ao provider. Se a chamada externa ou a atualizacao
 * posterior falharem, a transferencia local ja existe (rastreavel) — nunca ha Pix solicitado no
 * provider sem registro local correspondente.
 */
@Service
public class DesembolsoTransacaoService {

    private final PixTransferenciaRepository repository;
    private final SincronizadorStatusTransferencia sincronizador;

    public DesembolsoTransacaoService(
            PixTransferenciaRepository repository, SincronizadorStatusTransferencia sincronizador) {
        this.repository = repository;
        this.sincronizador = sincronizador;
    }

    /** Fase 1: persiste e comita a transferencia {@code CRIADA}. Corrida concorrente -> 409. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID inserirCriada(PixTransferencia transferencia, UUID contratoId) {
        try {
            return repository.saveAndFlush(transferencia).getId();
        } catch (DataIntegrityViolationException corrida) {
            // UNIQUE de idempotency_key (V45) ou UNIQUE parcial por contrato (V47) barra o perdedor de
            // uma corrida concorrente. Como a tx desta fase reverte sozinha, devolvemos 409 e o retry
            // sequencial do cliente cai no pre-check idempotente.
            throw new ConflitoException(
                    "PIX-409-CONFLITO-CONCORRENTE",
                    "Desembolso concorrente para o contrato " + contratoId + "; reapresente a solicitacao.");
        }
    }

    /** Fase 2 (sucesso do provider): aplica a resposta e comita o novo status + eventos. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PixTransferencia aplicarResposta(UUID transferenciaId, RespostaTransferenciaPix resposta) {
        PixTransferencia transferencia = carregar(transferenciaId);
        sincronizador.aplicarRespostaSolicitacao(transferencia, resposta);
        return repository.save(transferencia);
    }

    /** Fase 2 (falha tecnica do provider): marca {@code FALHOU} e comita (rastreavel + backoffice). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PixTransferencia marcarFalha(UUID transferenciaId, String motivo) {
        PixTransferencia transferencia = carregar(transferenciaId);
        sincronizador.marcarFalhaTecnica(transferencia, motivo);
        return repository.save(transferencia);
    }

    private PixTransferencia carregar(UUID id) {
        return repository
                .findById(id)
                .orElseThrow(() -> new IllegalStateException("PixTransferencia de desembolso desapareceu: " + id));
    }
}
