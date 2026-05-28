package com.dynamis.sep_api.credores.web.dto;

import com.dynamis.sep_api.credores.application.port.out.CarteiraCobrancaResumo;
import com.dynamis.sep_api.credores.domain.vo.StatusOperacaoFinanciada;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Representacao REST de uma operacao da carteira credora, enriquecida com cobranca. */
public record OperacaoCarteiraResponse(
        UUID id,
        UUID contratoId,
        UUID oportunidadeId,
        StatusOperacaoFinanciada status,
        String justificativa,
        BigDecimal valor,
        Integer prazoMeses,
        BigDecimal taxaJurosMensal,
        String contratoStatus,
        CarteiraCobrancaResumo cobranca,
        OffsetDateTime dataCriacao) {}
