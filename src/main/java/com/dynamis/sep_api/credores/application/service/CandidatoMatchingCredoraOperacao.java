package com.dynamis.sep_api.credores.application.service;

import com.dynamis.sep_api.credores.domain.vo.StatusCredora;
import com.dynamis.sep_api.credores.domain.vo.StatusElegibilidade;
import com.dynamis.sep_api.credores.domain.vo.StatusOperacaoFinanciada;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Snapshot minimo de um par (credora dona, operacao da propria carteira) submetido as regras de
 * elegibilidade de matching (Sprint 30 Task 30.1). Carrega somente identificadores tecnicos,
 * status e valores — sem CNPJ, razao social, score, dados bancarios ou qualquer PII.
 *
 * @param capacidadeAporte teto declarado no perfil da credora; {@code null} = nao informado (o
 *     criterio de capacidade nao e aplicado).
 * @param statusContrato status do contrato da operacao lido via porta cross-module (String para
 *     nao acoplar ao enum de {@code contratos}); {@code null} = contrato nao encontrado (dados
 *     insuficientes).
 * @param valorOperacao valor da oportunidade de origem da operacao; {@code null} = operacao sem
 *     oportunidade/valor (dados insuficientes).
 * @param parComMatchingExistente {@code true} se ja existe matching para o par em qualquer status
 *     — inclusive REJEITADA, para o refresh nao re-sugerir par ja decidido.
 */
public record CandidatoMatchingCredoraOperacao(
        UUID empresaCredoraId,
        UUID operacaoId,
        StatusCredora statusCredora,
        StatusElegibilidade elegibilidadeCredora,
        BigDecimal capacidadeAporte,
        StatusOperacaoFinanciada statusOperacao,
        String statusContrato,
        BigDecimal valorOperacao,
        boolean parComMatchingExistente) {}
