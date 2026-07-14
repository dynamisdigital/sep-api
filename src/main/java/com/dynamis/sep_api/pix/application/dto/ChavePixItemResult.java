package com.dynamis.sep_api.pix.application.dto;

import com.dynamis.sep_api.pix.domain.vo.StatusChavePix;
import com.dynamis.sep_api.pix.domain.vo.TipoChavePix;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Item da listagem de chaves Pix da conta operacional (Sprint 31 Task 31.6): somente campos
 * publicos — mascara, nunca valor bruto, hash, provider id ou idempotency key.
 */
public record ChavePixItemResult(
        UUID id,
        TipoChavePix tipo,
        String valorMascarado,
        StatusChavePix status,
        OffsetDateTime criadaEm,
        OffsetDateTime removidaEm) {}
