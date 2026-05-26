package com.dynamis.sep_api.backoffice.application.port.out;

import com.dynamis.sep_api.backoffice.application.port.out.dto.ResultadoReprocesso;
import com.dynamis.sep_api.backoffice.domain.vo.TipoChamadaProvider;

import java.util.UUID;

/**
 * Strategy GoF (Sprint 14 Task 14.4): cada implementacao retenta uma categoria de chamada a
 * provider externo. O {@code ProviderReprocessadorDispatcher} mantem o registry por
 * {@link TipoChamadaProvider}.
 */
public interface ProviderRetentativaStrategy {

    TipoChamadaProvider tipoSuportado();

    ResultadoReprocesso retentar(UUID entidadeId);
}
