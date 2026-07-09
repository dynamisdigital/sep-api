package com.dynamis.sep_api.credores.application.dto;

/**
 * Resultado do registro assistido de aporte (Sprint 29 Task 29.3). {@code novo} distingue criacao
 * (201) de replay idempotente (200) na borda web — padrao do desembolso Pix (Sprint 20).
 */
public record RegistrarAporteCredoraResult(AporteCredoraView aporte, boolean novo) {}
