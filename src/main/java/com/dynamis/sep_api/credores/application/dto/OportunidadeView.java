package com.dynamis.sep_api.credores.application.dto;

import com.dynamis.sep_api.credores.domain.model.OportunidadeInvestimento;
import com.dynamis.sep_api.credores.domain.vo.StatusOportunidade;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Projecao read-only de uma oportunidade de investimento para a credora. */
public record OportunidadeView(
        UUID id,
        UUID propostaId,
        UUID contratoId,
        BigDecimal valor,
        int prazoMeses,
        BigDecimal taxaJurosMensal,
        StatusOportunidade status,
        OffsetDateTime dataCriacao) {

    public static OportunidadeView de(OportunidadeInvestimento o) {
        return new OportunidadeView(
                o.getId(),
                o.getPropostaId(),
                o.getContratoId(),
                o.getValor(),
                o.getPrazoMeses(),
                o.getTaxaJurosMensal(),
                o.getStatus(),
                o.getDataCriacao());
    }
}
