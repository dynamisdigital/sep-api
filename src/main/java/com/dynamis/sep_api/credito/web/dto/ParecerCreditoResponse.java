package com.dynamis.sep_api.credito.web.dto;

import com.dynamis.sep_api.credito.domain.vo.DecisaoParecer;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Parecer registrado por operador financeiro. */
@Schema(description = "Parecer manual sobre proposta de credito")
public record ParecerCreditoResponse(
        UUID id,
        UUID propostaId,
        UUID pareceristaId,
        DecisaoParecer decisao,
        String justificativa,
        Integer scoreMotorSnapshot,
        int versao,
        OffsetDateTime dataParecer) {}
