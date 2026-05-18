package com.dynamis.sep_api.credito.web.mapper;

import com.dynamis.sep_api.credito.domain.model.ParecerCredito;
import com.dynamis.sep_api.credito.domain.model.PropostaCredito;
import com.dynamis.sep_api.credito.domain.model.RegraCreditoAvaliada;
import com.dynamis.sep_api.credito.domain.model.ScoreInterno;
import com.dynamis.sep_api.credito.web.dto.ParecerCreditoResponse;
import com.dynamis.sep_api.credito.web.dto.PropostaResponse;
import com.dynamis.sep_api.credito.web.dto.RegraAvaliadaResponse;
import com.dynamis.sep_api.credito.web.dto.ScoreInternoResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapeamento entre entidades de credito e DTOs web via MapStruct (ADR 0006).
 *
 * <p>{@code toResponse(PropostaCredito, ScoreInterno, ParecerCredito)} compoe a visao agregada
 * usada nos endpoints {@code GET /api/v1/credito/propostas/{id}} e listagem — score e parecer
 * podem ser null se ainda nao foram produzidos.
 */
@Mapper(componentModel = "spring")
public interface CreditoWebMapper {

    @Mapping(target = "score", source = "score")
    @Mapping(target = "parecer", source = "parecer")
    @Mapping(target = "id", source = "proposta.id")
    @Mapping(target = "tomadorId", source = "proposta.tomadorId")
    @Mapping(target = "solicitacaoOnboardingId", source = "proposta.solicitacaoOnboardingId")
    @Mapping(target = "tipoOperacao", source = "proposta.tipoOperacao")
    @Mapping(target = "valorSolicitado", source = "proposta.valorSolicitado")
    @Mapping(target = "moeda", source = "proposta.moeda")
    @Mapping(target = "prazoMeses", source = "proposta.prazoMeses")
    @Mapping(target = "status", source = "proposta.status")
    @Mapping(target = "dataCriacao", source = "proposta.dataCriacao")
    @Mapping(target = "dataModificacao", source = "proposta.dataModificacao")
    PropostaResponse toResponse(PropostaCredito proposta, ScoreInternoResponse score, ParecerCreditoResponse parecer);

    ScoreInternoResponse toScoreResponse(ScoreInterno score);

    ParecerCreditoResponse toParecerResponse(ParecerCredito parecer);

    RegraAvaliadaResponse toRegraResponse(RegraCreditoAvaliada regra);
}
