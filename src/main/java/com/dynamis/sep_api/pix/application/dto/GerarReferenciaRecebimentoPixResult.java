package com.dynamis.sep_api.pix.application.dto;

import com.dynamis.sep_api.pix.domain.vo.StatusPixReferenciaRecebimento;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Resultado da geracao de referencia Pix de recebimento (Sprint 21 Task 21.2). {@code novo}
 * distingue criacao (true) de retorno idempotente da referencia {@code ATIVA} ja existente (false).
 * Carrega apenas dados nao sensiveis necessarios ao pagamento (txid + copia-cola + valor).
 */
public record GerarReferenciaRecebimentoPixResult(
        UUID referenciaId,
        UUID parcelaId,
        String txid,
        String codigoCopiaCola,
        BigDecimal valorEsperado,
        StatusPixReferenciaRecebimento status,
        boolean novo) {}
