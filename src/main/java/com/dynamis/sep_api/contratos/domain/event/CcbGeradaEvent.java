package com.dynamis.sep_api.contratos.domain.event;

import java.util.UUID;

/**
 * Disparado apos {@code CcbGenerator} produzir o PDF da CCB (Sprint 11 Task 11.8). Audit listener
 * grava {@code CCB_GERADA} com hash do PDF gerado — sem o binario, conforme regra LGPD/CMN.
 */
public record CcbGeradaEvent(
        UUID contratoId, UUID propostaId, UUID tomadorId, UUID versaoId, int numeroVersao, String hashPdfGerado) {}
