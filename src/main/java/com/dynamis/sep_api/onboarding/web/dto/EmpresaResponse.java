package com.dynamis.sep_api.onboarding.web.dto;

import com.dynamis.sep_api.onboarding.domain.vo.StatusOnboarding;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Resposta minima apos criacao de solicitacao PJ — usada no POST de iniciacao. */
@Schema(description = "Resumo da solicitacao PJ criada")
public record EmpresaResponse(
        @Schema(example = "1f0799c0-98b9-6d9d-bc4a-7d6f5b771001") UUID id,
        @Schema(example = "INICIADO") StatusOnboarding status,
        @Schema(example = "27.865.757/0001-02", description = "CNPJ formatado") String cnpj,
        @Schema(example = "Acme Comercio LTDA") String razaoSocial,
        OffsetDateTime dataCriacao,
        OffsetDateTime dataModificacao) {}
