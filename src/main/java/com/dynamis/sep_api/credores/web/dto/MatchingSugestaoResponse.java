package com.dynamis.sep_api.credores.web.dto;

import com.dynamis.sep_api.credores.application.dto.MatchingCredoraOperacaoView;
import com.dynamis.sep_api.credores.domain.vo.StatusMatchingCredoraOperacao;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Representacao REST publica da sugestao de matching (Sprint 30). Contrato minimo da spec —
 * {@code criterios} sao codigos funcionais do snapshot. Nunca expoe motivo de decisao, decisor,
 * snapshot bruto, score, documento, dados bancarios, provider ou payload tecnico.
 */
public record MatchingSugestaoResponse(
        UUID id,
        UUID operacaoId,
        UUID empresaCredoraId,
        StatusMatchingCredoraOperacao status,
        BigDecimal valorElegivel,
        List<String> criterios,
        OffsetDateTime criadaEm,
        OffsetDateTime decididaEm) {

    public static MatchingSugestaoResponse de(MatchingCredoraOperacaoView view) {
        return new MatchingSugestaoResponse(
                view.id(),
                view.operacaoId(),
                view.empresaCredoraId(),
                view.status(),
                view.valorElegivel(),
                view.criterios(),
                view.criadaEm(),
                view.decididaEm());
    }
}
