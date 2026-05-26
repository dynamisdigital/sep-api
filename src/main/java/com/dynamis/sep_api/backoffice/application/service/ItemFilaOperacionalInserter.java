package com.dynamis.sep_api.backoffice.application.service;

import com.dynamis.sep_api.backoffice.application.service.CriarItemFilaOperacionalService.CriarItemCommand;
import com.dynamis.sep_api.backoffice.domain.event.ItemFilaCriadoEvent;
import com.dynamis.sep_api.backoffice.domain.model.ItemFilaOperacional;
import com.dynamis.sep_api.backoffice.infrastructure.persistence.ItemFilaOperacionalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Bean separado pra que o proxy AOP do Spring intercepte {@code @Transactional} corretamente —
 * self-invocation em {@link CriarItemFilaOperacionalService} (mesmo bean) nao seria interceptada,
 * e o catch fora da tx interna nao funcionaria.
 *
 * <p>Quando {@link #inserir} viola a UNIQUE parcial, a tx interna eh marcada para rollback e a
 * {@code DataIntegrityViolationException} sobe pra {@code CriarItemFilaOperacionalService}, que
 * ja nao tem tx propria pra propagar conflito.
 */
@Service
public class ItemFilaOperacionalInserter {

    private static final Logger LOG = LoggerFactory.getLogger(ItemFilaOperacionalInserter.class);

    private final ItemFilaOperacionalRepository repository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public ItemFilaOperacionalInserter(
            ItemFilaOperacionalRepository repository, ApplicationEventPublisher eventPublisher, Clock clock) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID inserir(CriarItemCommand cmd) {
        ItemFilaOperacional item = ItemFilaOperacional.abrir(
                cmd.tipo(),
                cmd.prioridade(),
                cmd.tipoEntidade(),
                cmd.entidadeId(),
                cmd.titulo(),
                cmd.descricao(),
                OffsetDateTime.now(clock));

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
        return salvo.getId();
    }
}
