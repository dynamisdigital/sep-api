package com.dynamis.sep_api.backoffice.application.usecase;

import com.dynamis.sep_api.backoffice.domain.event.ItemAssumidoEvent;
import com.dynamis.sep_api.backoffice.domain.exception.ItemFilaNaoEncontradoException;
import com.dynamis.sep_api.backoffice.domain.model.ItemFilaOperacional;
import com.dynamis.sep_api.backoffice.infrastructure.persistence.ItemFilaOperacionalRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Operador assume um item da fila (Sprint 14 Task 14.3). Transiciona {@code ABERTO ->
 * EM_TRATAMENTO} e registra {@code atribuidoA}. Transicao invalida vira HTTP 409 via
 * {@code TransicaoItemInvalidaException} (lancada pelo dominio).
 */
@Service
public class AssumirItemFilaUseCase {

    private final ItemFilaOperacionalRepository repository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public AssumirItemFilaUseCase(
            ItemFilaOperacionalRepository repository, ApplicationEventPublisher eventPublisher, Clock clock) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public ItemFilaOperacional executar(UUID itemId, UUID operadorId) {
        ItemFilaOperacional item = repository.findByIdForUpdate(itemId)
                .orElseThrow(() -> new ItemFilaNaoEncontradoException(itemId));
        item.assumir(operadorId);
        ItemFilaOperacional salvo = repository.save(item);
        eventPublisher.publishEvent(new ItemAssumidoEvent(salvo.getId(), operadorId, OffsetDateTime.now(clock)));
        return salvo;
    }
}
