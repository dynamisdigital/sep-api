package com.dynamis.sep_api.pix.application.dto;

import com.dynamis.sep_api.pix.domain.vo.StatusPixTransferencia;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Status atual de um desembolso Pix (Sprint 20 Task 20.3). Nunca expoe a chave em claro.
 *
 * @param providerIndisponivel {@code true} quando uma consulta ao provider foi tentada mas falhou e
 *     o status devolvido eh o local (fallback). A consulta-endpoint trata isso como leitura
 *     resiliente; o reprocesso operacional usa este sinal para nao reportar falso sucesso (Task 20.4
 *     code review).
 */
public record StatusDesembolsoPixResult(
        UUID transferenciaId,
        UUID contratoId,
        StatusPixTransferencia status,
        BigDecimal valor,
        String chaveDestinoMascara,
        boolean providerIndisponivel) {}
