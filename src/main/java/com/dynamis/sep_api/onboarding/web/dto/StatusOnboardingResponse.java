package com.dynamis.sep_api.onboarding.web.dto;

import com.dynamis.sep_api.onboarding.domain.vo.StatusOnboarding;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Visao completa do status — usada no GET. */
@Schema(description = "Status atual + documentos enviados + resultado da verificacao quando disponivel")
public record StatusOnboardingResponse(
        UUID id,
        StatusOnboarding status,
        OffsetDateTime dataCriacao,
        OffsetDateTime dataModificacao,
        List<DocumentoEnviadoResponse> documentosEnviados,
        @Schema(description = "Presente apenas quando ja existe resultado do KycProvider")
                ResultadoResponse resultado) {

    @Schema(description = "Resultado final do KycProvider")
    public record ResultadoResponse(
            StatusOnboarding statusFinal,
            @Schema(example = "documentos inconsistentes") String motivo,
            OffsetDateTime dataResultado) {}
}
