package com.dynamis.sep_api.onboarding.web.dto;

import com.dynamis.sep_api.onboarding.domain.vo.StatusPldRepresentante;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/**
 * Resumo publico do resultado PLD de um alvo (empresa ou representante). NUNCA expoe motivo,
 * severidade, base hit ou data de inclusao — detalhes ficam restritos a auditoria interna conforme
 * LGPD Art. 16.
 */
@Schema(description = "Resumo publico do PLD: apenas status consolidado + data da ultima consulta")
public record ConsultaPldResumoResponse(
        @Schema(example = "LIMPO") StatusPldRepresentante statusPld, OffsetDateTime dataConsulta) {}
