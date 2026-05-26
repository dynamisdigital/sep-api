package com.dynamis.sep_api.backoffice.application.port.out;

import com.dynamis.sep_api.backoffice.application.port.out.dto.ResultadoReprocesso;
import com.dynamis.sep_api.backoffice.domain.vo.TipoChamadaProvider;

import java.util.UUID;

/**
 * Porta de saida (Sprint 14 Task 14.4) — re-tenta chamada a provider externo via dispatcher
 * Strategy GoF. Tipos suportados estao em {@link TipoChamadaProvider}.
 */
public interface ProviderReprocessadorPort {

    ResultadoReprocesso reprocessar(TipoChamadaProvider tipo, UUID entidadeId);
}
