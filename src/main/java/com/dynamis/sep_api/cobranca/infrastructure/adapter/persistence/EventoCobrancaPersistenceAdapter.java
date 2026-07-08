package com.dynamis.sep_api.cobranca.infrastructure.adapter.persistence;

import com.dynamis.sep_api.cobranca.application.port.out.EventoCobrancaPort;
import com.dynamis.sep_api.cobranca.domain.model.EventoCobranca;
import com.dynamis.sep_api.cobranca.domain.vo.CanalNotificacao;
import com.dynamis.sep_api.cobranca.infrastructure.persistence.EventoCobrancaRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Adapter que traduz {@link EventoCobrancaPort} para o repository JPA (Sprint 28, ADR 0007).
 * Delegacao pura.
 */
@Component
public class EventoCobrancaPersistenceAdapter implements EventoCobrancaPort {

    private final EventoCobrancaRepository repository;

    public EventoCobrancaPersistenceAdapter(EventoCobrancaRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean jaNotificado(UUID parcelaId, Integer diasAtraso, CanalNotificacao canal, String template) {
        return repository.existsByParcelaIdAndDiasAtrasoAndCanalAndTemplate(parcelaId, diasAtraso, canal, template);
    }

    @Override
    public EventoCobranca salvar(EventoCobranca evento) {
        return repository.save(evento);
    }
}
