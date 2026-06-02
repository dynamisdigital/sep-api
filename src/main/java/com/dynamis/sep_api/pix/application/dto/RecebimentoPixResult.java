package com.dynamis.sep_api.pix.application.dto;

import com.dynamis.sep_api.pix.domain.vo.StatusPixRecebimento;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Projecao de leitura de um recebimento Pix para operacao assistida (Sprint 21 Task 21.6). Exposta
 * apenas a papeis internos. {@code endToEndId} eh o identificador tecnico do arranjo Pix (nao eh
 * segredo) — usado para conciliacao; nunca payload bruto nem chave Pix.
 */
public record RecebimentoPixResult(
        UUID recebimentoId,
        StatusPixRecebimento status,
        BigDecimal valor,
        String endToEndId,
        UUID referenciaId,
        UUID parcelaId,
        UUID recebimentoCobrancaId,
        String motivoDivergencia,
        OffsetDateTime recebidoEm) {}
