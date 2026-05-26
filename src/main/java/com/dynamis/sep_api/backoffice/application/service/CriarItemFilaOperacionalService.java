package com.dynamis.sep_api.backoffice.application.service;

import com.dynamis.sep_api.backoffice.domain.event.ItemFilaCriadoEvent;
import com.dynamis.sep_api.backoffice.domain.model.ItemFilaOperacional;
import com.dynamis.sep_api.backoffice.domain.vo.PrioridadeItem;
import com.dynamis.sep_api.backoffice.domain.vo.StatusItemFila;
import com.dynamis.sep_api.backoffice.domain.vo.TipoEntidadeReferenciada;
import com.dynamis.sep_api.backoffice.domain.vo.TipoItemFila;
import com.dynamis.sep_api.backoffice.infrastructure.persistence.ItemFilaOperacionalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Criacao idempotente de itens da fila operacional (Sprint 14 Task 14.2).
 *
 * <p>Garante que listeners e job consolidador nao criem itens duplicados pra mesma entidade do
 * mesmo tipo enquanto houver item ativo. Defesa em profundidade: check + catch de
 * {@link DataIntegrityViolationException} (UNIQUE parcial de {@code item_fila_operacional}) em
 * caso de race entre verificacao e insert.
 */
@Service
public class CriarItemFilaOperacionalService {

    private static final Logger LOG = LoggerFactory.getLogger(CriarItemFilaOperacionalService.class);

    private static final Set<StatusItemFila> ATIVOS = Set.of(StatusItemFila.ABERTO, StatusItemFila.EM_TRATAMENTO);

    private final ItemFilaOperacionalRepository repository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public CriarItemFilaOperacionalService(
            ItemFilaOperacionalRepository repository, ApplicationEventPublisher eventPublisher, Clock clock) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    public record CriarItemCommand(
            TipoItemFila tipo,
            PrioridadeItem prioridade,
            TipoEntidadeReferenciada tipoEntidade,
            UUID entidadeId,
            String titulo,
            String descricao) {}

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<UUID> criarSeAusente(CriarItemCommand cmd) {
        if (repository.existsByTipoAndTipoEntidadeAndEntidadeIdAndStatusIn(
                cmd.tipo(), cmd.tipoEntidade(), cmd.entidadeId(), ATIVOS)) {
            return Optional.empty();
        }

        ItemFilaOperacional item = ItemFilaOperacional.abrir(
                cmd.tipo(),
                cmd.prioridade(),
                cmd.tipoEntidade(),
                cmd.entidadeId(),
                cmd.titulo(),
                cmd.descricao(),
                OffsetDateTime.now(clock));

        try {
            ItemFilaOperacional salvo = repository.saveAndFlush(item);
            eventPublisher.publishEvent(new ItemFilaCriadoEvent(
                    salvo.getId(),
                    salvo.getTipo(),
                    salvo.getPrioridade(),
                    salvo.getTipoEntidade(),
                    salvo.getEntidadeId()));
            LOG.info(
                    "item fila criado id={} tipo={} prioridade={} entidade={}",
                    salvo.getId(),
                    salvo.getTipo(),
                    salvo.getPrioridade(),
                    salvo.getEntidadeId());
            return Optional.of(salvo.getId());
        } catch (DataIntegrityViolationException race) {
            LOG.info(
                    "race idempotencia detectada para tipo={} entidade={}; item duplicado nao criado",
                    cmd.tipo(),
                    cmd.entidadeId());
            return Optional.empty();
        }
    }
}
