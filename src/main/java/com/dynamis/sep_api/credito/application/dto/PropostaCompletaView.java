package com.dynamis.sep_api.credito.application.dto;

import com.dynamis.sep_api.credito.domain.model.ParecerCredito;
import com.dynamis.sep_api.credito.domain.model.PropostaCredito;
import com.dynamis.sep_api.credito.domain.model.ScoreInterno;

/**
 * Visao agregada de uma proposta retornada pelo {@code ConsultarPropostaCompletaUseCase} (Sprint
 * 8 Task 8.5 fix). Encapsula proposta + score atual (pode ser null se motor ainda nao avaliou) +
 * parecer mais recente (pode ser null se ainda nao houve parecer).
 *
 * <p>Existe pra evitar que o controller acesse {@code ScoreInternoRepository} /
 * {@code ParecerCreditoRepository} diretamente — preserva fronteira Hexagonal/DDD.
 */
public record PropostaCompletaView(PropostaCredito proposta, ScoreInterno score, ParecerCredito ultimoParecer) {}
