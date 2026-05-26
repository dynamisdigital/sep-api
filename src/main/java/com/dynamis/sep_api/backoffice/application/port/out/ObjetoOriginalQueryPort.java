package com.dynamis.sep_api.backoffice.application.port.out;

import com.dynamis.sep_api.backoffice.application.dto.ObjetoOriginalResumo;
import com.dynamis.sep_api.backoffice.domain.vo.TipoEntidadeReferenciada;

import java.util.Optional;
import java.util.UUID;

/**
 * Strategy GoF (Sprint 14 Task 14.3): cada implementacao resolve o objeto de dominio referenciado
 * por um {@link TipoEntidadeReferenciada}. O {@code ResolvedorObjetoOriginalDispatcher} mantem o
 * registry e despacha a chamada do {@code ConsultarItemFilaUseCase}.
 *
 * <p>Tipos sem strategy registrada retornam {@link Optional#empty()} (degradacao graciosa — o
 * detalhe basico do item ainda eh retornado, apenas sem {@code objetoOriginal}).
 */
public interface ObjetoOriginalQueryPort {

    TipoEntidadeReferenciada tipoSuportado();

    Optional<ObjetoOriginalResumo> buscar(UUID entidadeId);
}
