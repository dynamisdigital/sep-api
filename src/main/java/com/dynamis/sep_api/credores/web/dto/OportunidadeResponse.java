package com.dynamis.sep_api.credores.web.dto;

import com.dynamis.sep_api.credores.domain.vo.StatusOportunidade;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Representacao REST de uma oportunidade de investimento. */
public record OportunidadeResponse(
        UUID id,
        UUID propostaId,
        UUID contratoId,
        BigDecimal valor,
        int prazoMeses,
        BigDecimal taxaJurosMensal,
        StatusOportunidade status,
        OffsetDateTime dataCriacao) {}
