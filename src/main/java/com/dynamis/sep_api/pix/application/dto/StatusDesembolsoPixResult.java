package com.dynamis.sep_api.pix.application.dto;

import com.dynamis.sep_api.pix.domain.vo.StatusPixTransferencia;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Status atual de um desembolso Pix (Sprint 20 Task 20.3). Nunca expoe a chave em claro.
 *
 * @param providerConsultado {@code true} quando o provider foi efetivamente consultado com sucesso
 *     nesta operacao. {@code false} quando nao houve consulta (status terminal / sem external id /
 *     reconsulta nao solicitada) ou quando a consulta falhou.
 * @param providerIndisponivel {@code true} quando uma consulta ao provider foi tentada mas falhou e
 *     o status devolvido eh o local (fallback). A consulta-endpoint trata isso como leitura
 *     resiliente; o reprocesso operacional usa estes sinais para nao reportar falso sucesso (code
 *     review Tasks 20.4/20.5).
 */
public record StatusDesembolsoPixResult(
        UUID transferenciaId,
        UUID contratoId,
        StatusPixTransferencia status,
        BigDecimal valor,
        String chaveDestinoMascara,
        boolean providerConsultado,
        boolean providerIndisponivel) {}
