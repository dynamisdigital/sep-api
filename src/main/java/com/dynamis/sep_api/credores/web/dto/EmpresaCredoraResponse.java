package com.dynamis.sep_api.credores.web.dto;

import com.dynamis.sep_api.credores.domain.vo.StatusCredora;
import com.dynamis.sep_api.credores.domain.vo.StatusElegibilidade;
import com.dynamis.sep_api.credores.domain.vo.TipoCredora;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Representacao REST de uma empresa credora e seu perfil operacional. CNPJ formatado. */
public record EmpresaCredoraResponse(
        UUID id,
        UUID usuarioId,
        UUID onboardingId,
        String cnpj,
        String razaoSocial,
        StatusCredora status,
        StatusElegibilidade elegibilidade,
        String motivoInelegibilidade,
        TipoCredora tipoCredora,
        BigDecimal capacidadeAporte,
        OffsetDateTime dataCriacao,
        OffsetDateTime dataModificacao) {}
