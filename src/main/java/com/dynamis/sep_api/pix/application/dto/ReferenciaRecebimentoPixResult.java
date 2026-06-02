package com.dynamis.sep_api.pix.application.dto;

import com.dynamis.sep_api.pix.domain.vo.StatusPixReferenciaRecebimento;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Projecao de leitura de uma referencia Pix de recebimento (Sprint 21 Task 21.6). So dados nao
 * sensiveis: ids tecnicos, txid, copia-cola, valor e status.
 */
public record ReferenciaRecebimentoPixResult(
        UUID referenciaId,
        UUID parcelaId,
        UUID contratoId,
        String txid,
        String codigoCopiaCola,
        BigDecimal valorEsperado,
        StatusPixReferenciaRecebimento status) {}
