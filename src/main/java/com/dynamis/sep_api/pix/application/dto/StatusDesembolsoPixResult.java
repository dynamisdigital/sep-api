package com.dynamis.sep_api.pix.application.dto;

import com.dynamis.sep_api.pix.domain.vo.StatusPixTransferencia;

import java.math.BigDecimal;
import java.util.UUID;

/** Status atual de um desembolso Pix (Sprint 20 Task 20.3). Nunca expoe a chave em claro. */
public record StatusDesembolsoPixResult(
        UUID transferenciaId,
        UUID contratoId,
        StatusPixTransferencia status,
        BigDecimal valor,
        String chaveDestinoMascara) {}
