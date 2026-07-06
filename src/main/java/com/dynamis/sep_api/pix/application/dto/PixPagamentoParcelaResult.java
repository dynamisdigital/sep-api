package com.dynamis.sep_api.pix.application.dto;

import com.dynamis.sep_api.pix.domain.vo.StatusPixParcelaPublico;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Resultado publico do estado Pix de uma parcela do tomador (Sprint 26 — Gate P2). Recorte minimo:
 * status publico, valor esperado, instante de atualizacao da fonte vencedora e mensagem publica
 * sanitizada (apenas em estados de atencao). Sem txid, copia-cola, IDs internos ou motivo tecnico.
 */
public record PixPagamentoParcelaResult(
        StatusPixParcelaPublico status, BigDecimal valor, OffsetDateTime atualizadoEm, String mensagemPublica) {}
