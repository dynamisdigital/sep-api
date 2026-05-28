package com.dynamis.sep_api.credores.web.dto;

import com.dynamis.sep_api.credores.domain.vo.StatusInteresseCredora;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Representacao REST de uma manifestacao de interesse da credora. */
public record InteresseResponse(
        UUID id, UUID oportunidadeId, StatusInteresseCredora status, OffsetDateTime dataCriacao) {}
