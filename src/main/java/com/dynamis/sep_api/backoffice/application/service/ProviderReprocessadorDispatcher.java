package com.dynamis.sep_api.backoffice.application.service;

import com.dynamis.sep_api.backoffice.application.port.out.ProviderReprocessadorPort;
import com.dynamis.sep_api.backoffice.application.port.out.ProviderRetentativaStrategy;
import com.dynamis.sep_api.backoffice.application.port.out.dto.ResultadoReprocesso;
import com.dynamis.sep_api.backoffice.domain.exception.TipoReprocessoNaoSuportadoException;
import com.dynamis.sep_api.backoffice.domain.vo.TipoChamadaProvider;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Strategy dispatcher (Sprint 14 Task 14.4): registry de {@link ProviderRetentativaStrategy} por
 * {@link TipoChamadaProvider}. Tipo sem strategy registrada lanca
 * {@link TipoReprocessoNaoSuportadoException} — mapeada para 400 pelo
 * {@code ApiExceptionHandler} (fix review manual Task 14.7).
 */
@Component
public class ProviderReprocessadorDispatcher implements ProviderReprocessadorPort {

    private final Map<TipoChamadaProvider, ProviderRetentativaStrategy> registry;

    public ProviderReprocessadorDispatcher(List<ProviderRetentativaStrategy> strategies) {
        EnumMap<TipoChamadaProvider, ProviderRetentativaStrategy> map = new EnumMap<>(TipoChamadaProvider.class);
        for (ProviderRetentativaStrategy s : strategies) {
            map.put(s.tipoSuportado(), s);
        }
        this.registry = Map.copyOf(map);
    }

    @Override
    public ResultadoReprocesso reprocessar(TipoChamadaProvider tipo, UUID entidadeId) {
        ProviderRetentativaStrategy strategy = registry.get(tipo);
        if (strategy == null) {
            throw new TipoReprocessoNaoSuportadoException(tipo);
        }
        return strategy.retentar(entidadeId);
    }
}
