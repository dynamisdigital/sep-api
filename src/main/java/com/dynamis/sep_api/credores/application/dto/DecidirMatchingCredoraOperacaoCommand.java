package com.dynamis.sep_api.credores.application.dto;

import java.util.UUID;

/**
 * Comando da decisao assistida do matching (Sprint 30 Task 30.4). {@code motivo} e opcional; o
 * texto e normalizado/limitado no dominio e persistido apenas sanitizado. {@code atorId} e o
 * operador financeiro/admin autenticado com step-up estrito na borda.
 */
public record DecidirMatchingCredoraOperacaoCommand(
        UUID sugestaoId, AcaoDecisaoMatching acao, String motivo, UUID atorId) {}
