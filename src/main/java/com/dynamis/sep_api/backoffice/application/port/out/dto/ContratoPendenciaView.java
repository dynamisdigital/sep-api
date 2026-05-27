package com.dynamis.sep_api.backoffice.application.port.out.dto;

import java.util.UUID;

/** Projecao minima de contrato parado em ACEITO (Sprint 14 Task 14.2 — fix review manual). */
public record ContratoPendenciaView(UUID contratoId) {}
