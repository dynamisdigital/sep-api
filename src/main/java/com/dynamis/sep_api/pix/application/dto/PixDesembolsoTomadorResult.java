package com.dynamis.sep_api.pix.application.dto;

import com.dynamis.sep_api.pix.domain.vo.StatusPixPublico;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Resultado publico da consulta de desembolso Pix do tomador (Sprint 26 — Gate P1). Recorte minimo:
 * status publico, valor e instante de ultima atualizacao. Sem chave, txid, IDs internos ou provider.
 */
public record PixDesembolsoTomadorResult(StatusPixPublico status, BigDecimal valor, OffsetDateTime atualizadoEm) {}
