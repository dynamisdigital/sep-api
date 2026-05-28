package com.dynamis.sep_api.credores.application.dto;

import java.util.UUID;

/**
 * Comando de associacao operacional assistida de uma operacao financiada a carteira de uma credora
 * (Sprint 17). {@code oportunidadeId} opcional; quando presente, deriva o contrato da oportunidade.
 *
 * @param empresaCredoraId credora alvo da associacao
 * @param contratoId contrato a associar (obrigatorio quando nao ha oportunidadeId)
 * @param oportunidadeId oportunidade de origem (opcional)
 * @param justificativa motivacao operacional da associacao
 * @param atorId usuario que dispara a associacao (para auditoria)
 */
public record AssociarOperacaoFinanciadaCommand(
        UUID empresaCredoraId, UUID contratoId, UUID oportunidadeId, String justificativa, UUID atorId) {}
