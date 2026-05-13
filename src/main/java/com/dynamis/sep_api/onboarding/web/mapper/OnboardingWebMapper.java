package com.dynamis.sep_api.onboarding.web.mapper;

import com.dynamis.sep_api.onboarding.application.dto.StatusOnboardingView;
import com.dynamis.sep_api.onboarding.domain.model.SolicitacaoOnboarding;
import com.dynamis.sep_api.onboarding.web.dto.DocumentoEnviadoResponse;
import com.dynamis.sep_api.onboarding.web.dto.OnboardingResponse;
import com.dynamis.sep_api.onboarding.web.dto.StatusOnboardingResponse;
import org.mapstruct.Mapper;

/** MapStruct: dominio/view application -> DTOs web. */
@Mapper
public interface OnboardingWebMapper {

    default OnboardingResponse toOnboardingResponse(SolicitacaoOnboarding s) {
        return new OnboardingResponse(s.getId(), s.getStatus(), s.getDataCriacao(), s.getDataModificacao());
    }

    default StatusOnboardingResponse toStatusResponse(StatusOnboardingView view) {
        return new StatusOnboardingResponse(
                view.id(),
                view.status(),
                view.dataCriacao(),
                view.dataModificacao(),
                view.documentosEnviados().stream()
                        .map(d -> new DocumentoEnviadoResponse(d.id(), d.tipo(), d.dataEnvio(), d.sha256()))
                        .toList(),
                view.resultado() == null
                        ? null
                        : new StatusOnboardingResponse.ResultadoResponse(
                                view.resultado().statusFinal(),
                                view.resultado().motivo(),
                                view.resultado().dataResultado()));
    }
}
