package com.dynamis.sep_api.backoffice.application.usecase;

import com.dynamis.sep_api.backoffice.domain.event.ItemResolvidoEvent;
import com.dynamis.sep_api.backoffice.domain.exception.ItemFilaNaoEncontradoException;
import com.dynamis.sep_api.backoffice.domain.exception.JustificativaInvalidaException;
import com.dynamis.sep_api.backoffice.domain.model.ComentarioInterno;
import com.dynamis.sep_api.backoffice.domain.model.ItemFilaOperacional;
import com.dynamis.sep_api.backoffice.infrastructure.persistence.ComentarioInternoRepository;
import com.dynamis.sep_api.backoffice.infrastructure.persistence.ItemFilaOperacionalRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Marca item como {@code RESOLVIDO} (Sprint 14 Task 14.3). Justificativa minima de 20 caracteres
 * eh persistida como {@link ComentarioInterno} antes da transicao. Step-up MFA aplicado na borda
 * REST (Task 14.7) — use case nao valida step-up diretamente.
 */
@Service
public class MarcarItemResolvidoUseCase {

    private static final int RESUMO_MAX_CARACTERES = 80;

    private final ItemFilaOperacionalRepository itemRepository;
    private final ComentarioInternoRepository comentarioRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public MarcarItemResolvidoUseCase(
            ItemFilaOperacionalRepository itemRepository,
            ComentarioInternoRepository comentarioRepository,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.itemRepository = itemRepository;
        this.comentarioRepository = comentarioRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public ItemFilaOperacional executar(UUID itemId, UUID operadorId, String justificativa) {
        validarJustificativa(justificativa);

        ItemFilaOperacional item =
                itemRepository.findByIdForUpdate(itemId).orElseThrow(() -> new ItemFilaNaoEncontradoException(itemId));

        comentarioRepository.save(ComentarioInterno.registrar(itemId, operadorId, justificativa));

        item.resolver(OffsetDateTime.now(clock));
        ItemFilaOperacional salvo = itemRepository.save(item);

        eventPublisher.publishEvent(new ItemResolvidoEvent(salvo.getId(), operadorId, resumir(justificativa)));
        return salvo;
    }

    private static void validarJustificativa(String justificativa) {
        if (justificativa == null || justificativa.strip().length() < JustificativaInvalidaException.MIN_CARACTERES) {
            throw new JustificativaInvalidaException();
        }
    }

    private static String resumir(String texto) {
        if (texto.length() <= RESUMO_MAX_CARACTERES) {
            return texto;
        }
        return texto.substring(0, RESUMO_MAX_CARACTERES) + "...";
    }
}
