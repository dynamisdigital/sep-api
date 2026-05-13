package com.dynamis.sep_api.onboarding.application.dto;

import com.dynamis.sep_api.onboarding.domain.vo.StatusOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.TipoDocumento;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Visao composta de uma solicitacao para consulta de status. */
public record StatusOnboardingView(
        UUID id,
        StatusOnboarding status,
        OffsetDateTime dataCriacao,
        OffsetDateTime dataModificacao,
        List<DocumentoEnviado> documentosEnviados,
        ResultadoView resultado) {

    public record DocumentoEnviado(UUID id, TipoDocumento tipo, OffsetDateTime dataEnvio, String sha256) {}

    public record ResultadoView(StatusOnboarding statusFinal, String motivo, OffsetDateTime dataResultado) {}
}
