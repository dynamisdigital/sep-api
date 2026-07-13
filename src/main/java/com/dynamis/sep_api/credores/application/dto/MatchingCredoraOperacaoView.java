package com.dynamis.sep_api.credores.application.dto;

import com.dynamis.sep_api.credores.domain.model.MatchingCredoraOperacao;
import com.dynamis.sep_api.credores.domain.vo.StatusMatchingCredoraOperacao;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Projecao de leitura da sugestao de matching para consulta operacional (Sprint 30). {@code
 * criterios} sao os codigos funcionais congelados no snapshot da sugestao. Nao carrega motivo de
 * decisao, decisor ou qualquer dado de contrato/credora alem dos identificadores tecnicos.
 */
public record MatchingCredoraOperacaoView(
        UUID id,
        UUID empresaCredoraId,
        UUID operacaoId,
        StatusMatchingCredoraOperacao status,
        BigDecimal valorElegivel,
        List<String> criterios,
        OffsetDateTime criadaEm,
        OffsetDateTime decididaEm) {

    public static MatchingCredoraOperacaoView de(MatchingCredoraOperacao matching) {
        return new MatchingCredoraOperacaoView(
                matching.getId(),
                matching.getEmpresaCredoraId(),
                matching.getOperacaoId(),
                matching.getStatus(),
                matching.getValorElegivel(),
                List.of(matching.getCriteriosSnapshot().split(";")),
                matching.getDataCriacao(),
                matching.getDataDecisao());
    }
}
