package com.dynamis.sep_api.pix.application.dto;

import com.dynamis.sep_api.pix.domain.vo.StatusChavePix;
import com.dynamis.sep_api.pix.domain.vo.TipoChavePix;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Resultado do cadastro assistido de chave Pix (Sprint 31): somente campos publicos (mascara,
 * nunca valor/hash/provider id). {@code novo} distingue criacao (201) de replay idempotente (200)
 * na borda REST e nao entra no DTO de resposta.
 */
public record CadastrarChavePixResult(
        UUID id,
        TipoChavePix tipo,
        String valorMascarado,
        StatusChavePix status,
        OffsetDateTime criadaEm,
        OffsetDateTime removidaEm,
        boolean novo) {}
