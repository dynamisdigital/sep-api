package com.dynamis.sep_api.cobranca.application.usecase;

import com.dynamis.sep_api.cobranca.application.port.out.EventoCobrancaPort;
import com.dynamis.sep_api.cobranca.application.port.out.ParcelaCobrancaPort;
import com.dynamis.sep_api.cobranca.domain.event.EventoCobrancaRegistradoEvent;
import com.dynamis.sep_api.cobranca.domain.exception.ParcelaCobrancaNaoEncontradaException;
import com.dynamis.sep_api.cobranca.domain.model.EventoCobranca;
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

    private final ParcelaCobrancaPort parcelaPort;
    private final EventoCobrancaPort eventoPort;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public RegistrarContatoCobrancaUseCase(
            ParcelaCobrancaPort parcelaPort,
            EventoCobrancaPort eventoPort,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.parcelaPort = parcelaPort;
        this.eventoPort = eventoPort;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public EventoCobranca executar(UUID parcelaId, UUID registradoPor, Integer diasAtraso, String descricao) {
        if (!parcelaPort.existePorId(parcelaId)) {
            throw ParcelaCobrancaNaoEncontradaException.porId(parcelaId);
        }
        EventoCobranca evento = EventoCobranca.contatoManual(
                parcelaId, registradoPor, diasAtraso, descricao, OffsetDateTime.now(clock));
        evento = eventoPort.salvar(evento);
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
