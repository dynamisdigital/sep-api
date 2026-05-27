package com.dynamis.sep_api.backoffice.application.service;

import com.dynamis.sep_api.backoffice.application.dto.ObjetoOriginalResumo;
import com.dynamis.sep_api.backoffice.application.port.out.ObjetoOriginalQueryPort;
import com.dynamis.sep_api.backoffice.domain.vo.TipoEntidadeReferenciada;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Strategy dispatcher (Sprint 14 Task 14.3): mantem registry de {@link ObjetoOriginalQueryPort}
 * por {@link TipoEntidadeReferenciada} e despacha pra strategy correspondente. Falha na strategy
 * (excecao runtime) eh logada e devolve {@link Optional#empty()} — degradacao graciosa: o
 * {@code ConsultarItemFilaUseCase} ainda devolve o detalhe basico do item, apenas sem o
 * {@code objetoOriginal}.
 */
@Component
public class ResolvedorObjetoOriginalDispatcher {

    private static final Logger LOG = LoggerFactory.getLogger(ResolvedorObjetoOriginalDispatcher.class);

    private final Map<TipoEntidadeReferenciada, ObjetoOriginalQueryPort> registry;

    public ResolvedorObjetoOriginalDispatcher(List<ObjetoOriginalQueryPort> strategies) {
        EnumMap<TipoEntidadeReferenciada, ObjetoOriginalQueryPort> map = new EnumMap<>(TipoEntidadeReferenciada.class);
        for (ObjetoOriginalQueryPort s : strategies) {
            map.put(s.tipoSuportado(), s);
        }
        this.registry = Map.copyOf(map);
    }

    public Optional<ObjetoOriginalResumo> resolver(TipoEntidadeReferenciada tipo, UUID entidadeId) {
        ObjetoOriginalQueryPort strategy = registry.get(tipo);
        if (strategy == null) {
            return Optional.empty();
        }
        try {
            return strategy.buscar(entidadeId);
        } catch (RuntimeException ex) {
            LOG.warn("falha ao resolver objeto original tipo={} id={}", tipo, entidadeId, ex);
            return Optional.empty();
        }
    }
}
