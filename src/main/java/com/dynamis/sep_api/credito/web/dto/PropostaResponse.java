package com.dynamis.sep_api.credito.web.dto;

import com.dynamis.sep_api.credito.domain.vo.StatusProposta;
import com.dynamis.sep_api.credito.domain.vo.TipoOperacao;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Visao consolidada de uma proposta — inclui score do motor e ultimo parecer (quando existir). */
@Schema(description = "Proposta de credito + score atual + ultimo parecer")
public record PropostaResponse(
        UUID id,
        UUID tomadorId,
        UUID solicitacaoOnboardingId,
        TipoOperacao tipoOperacao,
        BigDecimal valorSolicitado,
        String moeda,
        int prazoMeses,
        StatusProposta status,
        OffsetDateTime dataCriacao,
        OffsetDateTime dataModificacao,
        @Schema(description = "Score interno mais recente (null se motor ainda nao avaliou)")
                ScoreInternoResponse score,
        @Schema(description = "Parecer mais recente (null se ainda nao houve parecer)")
                ParecerCreditoResponse parecer) {}
