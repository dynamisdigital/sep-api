package com.dynamis.sep_api.backoffice.application.usecase;

import com.dynamis.sep_api.backoffice.application.dto.ComentarioInternoSummary;
import com.dynamis.sep_api.backoffice.application.dto.ItemFilaDetalhe;
import com.dynamis.sep_api.backoffice.application.dto.ObjetoOriginalResumo;
import com.dynamis.sep_api.backoffice.application.service.ResolvedorObjetoOriginalDispatcher;
import com.dynamis.sep_api.backoffice.domain.exception.ItemFilaNaoEncontradoException;
import com.dynamis.sep_api.backoffice.domain.model.ItemFilaOperacional;
import com.dynamis.sep_api.backoffice.infrastructure.persistence.ComentarioInternoRepository;
import com.dynamis.sep_api.backoffice.infrastructure.persistence.ItemFilaOperacionalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Consulta detalhada de um item da fila (Sprint 14 Task 14.3). Carrega o item, comentarios
 * vinculados e tenta resolver o objeto original via {@link ResolvedorObjetoOriginalDispatcher}.
 */
@Service
public class ConsultarItemFilaUseCase {

    private final ItemFilaOperacionalRepository itemRepository;
    private final ComentarioInternoRepository comentarioRepository;
    private final ResolvedorObjetoOriginalDispatcher resolvedor;

    public ConsultarItemFilaUseCase(
            ItemFilaOperacionalRepository itemRepository,
            ComentarioInternoRepository comentarioRepository,
            ResolvedorObjetoOriginalDispatcher resolvedor) {
        this.itemRepository = itemRepository;
        this.comentarioRepository = comentarioRepository;
        this.resolvedor = resolvedor;
    }

    @Transactional(readOnly = true)
    public ItemFilaDetalhe consultar(UUID itemId) {
        ItemFilaOperacional item = itemRepository
                .findById(itemId)
                .orElseThrow(() -> new ItemFilaNaoEncontradoException(itemId));

        List<ComentarioInternoSummary> comentarios = comentarioRepository
                .findByItemIdOrderByDataCriacaoAsc(itemId)
                .stream()
                .map(ComentarioInternoSummary::de)
                .toList();

        ObjetoOriginalResumo objetoOriginal = resolvedor
                .resolver(item.getTipoEntidade(), item.getEntidadeId())
                .orElse(null);

        return ItemFilaDetalhe.de(item, comentarios, objetoOriginal);
    }
}
