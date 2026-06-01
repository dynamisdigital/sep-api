package com.dynamis.sep_api.pix.application.dto;

import com.dynamis.sep_api.pix.domain.vo.StatusPixTransferencia;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Resultado da solicitacao de desembolso (Sprint 20 Task 20.2). {@code novo=false} indica retorno
 * idempotente de uma transferencia ja existente. Nunca expoe a chave Pix em claro — apenas a
 * mascara.
 */
public record SolicitarDesembolsoPixResult(
        UUID transferenciaId,
        UUID contratoId,
        StatusPixTransferencia status,
        BigDecimal valor,
        String chaveDestinoMascara,
        boolean novo) {}
