package com.dynamis.sep_api.cobranca.application.dto;

import com.dynamis.sep_api.cobranca.domain.vo.StatusParcela;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Resultado do {@code RegistrarRecebimentoUseCase}. {@code novo} indica se este recebimento foi
 * de fato persistido (false = reapresentacao idempotente da mesma {@code idempotencyKey}).
 */
public record RegistrarRecebimentoResult(
        UUID recebimentoId,
        UUID parcelaId,
        StatusParcela statusParcela,
        BigDecimal valorRecebido,
        OffsetDateTime dataRecebimento,
        String meioPagamento,
        String identificadorExterno,
        UUID movimentacaoEscrowId,
        boolean novo) {}
