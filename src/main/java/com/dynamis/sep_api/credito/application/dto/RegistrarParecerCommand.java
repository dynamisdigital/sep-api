package com.dynamis.sep_api.credito.application.dto;

import com.dynamis.sep_api.credito.domain.vo.DecisaoParecer;

import java.util.UUID;

/**
 * Command de entrada do {@code RegistrarParecerUseCase} (Sprint 8 Task 8.4). Autorizacao
 * (ROLE_FINANCEIRO + step-up) e aplicada no controller (Spring Security).
 */
public record RegistrarParecerCommand(
        UUID propostaId, UUID pareceristaId, DecisaoParecer decisao, String justificativa) {}
