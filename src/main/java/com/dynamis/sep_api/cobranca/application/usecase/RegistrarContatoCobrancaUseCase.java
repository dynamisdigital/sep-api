package com.dynamis.sep_api.cobranca.application.usecase;

import com.dynamis.sep_api.cobranca.domain.event.EventoCobrancaRegistradoEvent;
import com.dynamis.sep_api.cobranca.domain.exception.ParcelaCobrancaNaoEncontradaException;
import com.dynamis.sep_api.cobranca.domain.model.EventoCobranca;
import com.dynamis.sep_api.cobranca.infrastructure.persistence.EventoCobrancaRepository;
import com.dynamis.sep_api.cobranca.infrastructure.persistence.ParcelaCobrancaRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Registra contato manual do financeiro/admin com o tomador (Sprint 13 Task 13.7). Nao altera
 * status da parcela — apenas grava {@link EventoCobranca} para trilha operacional.
 */
@Service
public class RegistrarContatoCobrancaUseCase {

    private final ParcelaCobrancaRepository parcelaRepository;
    private final EventoCobrancaRepository eventoRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public RegistrarContatoCobrancaUseCase(
            ParcelaCobrancaRepository parcelaRepository,
            EventoCobrancaRepository eventoRepository,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.parcelaRepository = parcelaRepository;
        this.eventoRepository = eventoRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public EventoCobranca executar(UUID parcelaId, UUID registradoPor, Integer diasAtraso, String descricao) {
        if (!parcelaRepository.existsById(parcelaId)) {
            throw ParcelaCobrancaNaoEncontradaException.porId(parcelaId);
        }
        EventoCobranca evento = EventoCobranca.contatoManual(
                parcelaId, registradoPor, diasAtraso, descricao, OffsetDateTime.now(clock));
        evento = eventoRepository.save(evento);
        eventPublisher.publishEvent(new EventoCobrancaRegistradoEvent(
                evento.getId(),
                parcelaId,
                evento.getTipo(),
                evento.getStatus(),
                evento.getCanal(),
                evento.getTemplate(),
                diasAtraso,
                registradoPor));
        return evento;
    }
}
