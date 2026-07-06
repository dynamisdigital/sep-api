package com.dynamis.sep_api.credores.application.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Projecao do status Pix de uma operacao da carteira da credora (Sprint 26 — Gate P3), retornada
 * pela porta {@code PixOperacaoStatusQueryPort}. O status cruza a fronteira como {@code String}
 * (nome do estado publico), para o modulo {@code credores} nao depender do dominio de {@code pix}.
 */
public record PixOperacaoStatusView(String status, BigDecimal valor, OffsetDateTime atualizadoEm) {}
