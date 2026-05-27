package com.dynamis.sep_api.backoffice.application.usecase;

import com.dynamis.sep_api.backoffice.domain.event.ComentarioRegistradoEvent;
import com.dynamis.sep_api.backoffice.domain.exception.ItemFilaNaoEncontradoException;
import com.dynamis.sep_api.backoffice.domain.model.ComentarioInterno;
import com.dynamis.sep_api.backoffice.infrastructure.persistence.ComentarioInternoRepository;
import com.dynamis.sep_api.backoffice.infrastructure.persistence.ItemFilaOperacionalRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Registra comentario interno em um item da fila (Sprint 14 Task 14.3). Permitido em qualquer status. */
@Service
public class RegistrarComentarioUseCase {

    private static final int RESUMO_MAX_CARACTERES = 80;

    private final ItemFilaOperacionalRepository itemRepository;
    private final ComentarioInternoRepository comentarioRepository;
    private final ApplicationEventPublisher eventPublisher;

    public RegistrarComentarioUseCase(
            ItemFilaOperacionalRepository itemRepository,
            ComentarioInternoRepository comentarioRepository,
            ApplicationEventPublisher eventPublisher) {
        this.itemRepository = itemRepository;
        this.comentarioRepository = comentarioRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public ComentarioInterno executar(UUID itemId, UUID autorId, String conteudo) {
        if (!itemRepository.existsById(itemId)) {
            throw new ItemFilaNaoEncontradoException(itemId);
        }
        ComentarioInterno comentario = ComentarioInterno.registrar(itemId, autorId, conteudo);
        ComentarioInterno salvo = comentarioRepository.save(comentario);
        eventPublisher.publishEvent(new ComentarioRegistradoEvent(itemId, salvo.getId(), autorId, resumir(conteudo)));
        return salvo;
    }

    private static String resumir(String conteudo) {
        if (conteudo.length() <= RESUMO_MAX_CARACTERES) {
            return conteudo;
        }
        return conteudo.substring(0, RESUMO_MAX_CARACTERES) + "...";
    }
}
