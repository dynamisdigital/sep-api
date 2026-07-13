package com.dynamis.sep_api.credores.web.dto;

import com.dynamis.sep_api.credores.application.dto.AcaoDecisaoMatching;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Corpo da decisao assistida do matching (Sprint 30). {@code acao} invalida no JSON resulta em
 * 400 (HttpMessageNotReadable); {@code motivo} e opcional e sanitizado no dominio.
 */
public record DecidirMatchingRequest(
        @NotNull(message = "acao deve ser CONFIRMAR ou REJEITAR") AcaoDecisaoMatching acao,
        @Size(max = 255, message = "motivo nao pode exceder 255 caracteres") String motivo) {}
