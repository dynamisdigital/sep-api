package com.dynamis.sep_api.governanca.application.dto;

import java.util.UUID;

/**
 * Comando de alteracao de parametro operacional (Sprint 18).
 *
 * @param chave chave canonica do parametro
 * @param novoValor novo valor (validado conforme o tipo)
 * @param justificativa motivacao da alteracao (obrigatoria, vira historico)
 * @param atorId admin que dispara a alteracao
 */
public record AlterarParametroCommand(String chave, String novoValor, String justificativa, UUID atorId) {}
