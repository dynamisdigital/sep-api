package com.dynamis.sep_api.pix.application.service;

import com.dynamis.sep_api.pix.domain.model.PixRecebimento;
import com.dynamis.sep_api.pix.infrastructure.persistence.PixRecebimentoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fronteira transacional do insert do {@link PixRecebimento} (Sprint 21 Task 21.3 — fix de corrida do
 * code review). Bean separado para que o {@code saveAndFlush} abra a propria tx
 * {@link Propagation#REQUIRES_NEW} (proxy AOP nao intercepta self-invocation no use case).
 *
 * <p>Numa corrida concorrente (mesmo {@code end_to_end_id} em eventos de id distinto), a unique
 * parcial de {@code end_to_end_id} (V45) falha isolada nesta tx, que reverte sozinha, sem contaminar
 * a tx do webhook (deixa-la rollback-only mascararia o {@code PROCESSADO} do evento). A
 * {@code DataIntegrityViolationException} propaga limpa para o caller, que a trata como duplicidade
 * idempotente — nunca ha baixa duplicada.
 */
@Service
public class PixRecebimentoTransacaoService {

    private final PixRecebimentoRepository repository;

    public PixRecebimentoTransacaoService(PixRecebimentoRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistir(PixRecebimento recebimento) {
        repository.saveAndFlush(recebimento);
    }
}
