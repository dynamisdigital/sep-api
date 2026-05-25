package com.dynamis.sep_api.cobranca.web.dto;

import com.dynamis.sep_api.cobranca.domain.model.Renegociacao;
import com.dynamis.sep_api.cobranca.domain.vo.StatusParcela;
import com.dynamis.sep_api.cobranca.domain.vo.StatusRenegociacao;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "Detalhe da renegociacao retornado pelos endpoints REST (Sprint 13).")
public record RenegociacaoResponse(
        @Schema(description = "UUID da renegociacao.") UUID id,
        @Schema(description = "UUID da parcela original.") UUID parcelaOriginalId,
        @Schema(description = "UUID da agenda original.") UUID agendaOriginalId,
        @Schema(description = "UUID do tomador.") UUID tomadorId,
        @Schema(description = "Status atual.") StatusRenegociacao status,
        @Schema(description = "Status da parcela antes de EM_NEGOCIACAO.") StatusParcela statusParcelaAnterior,
        @Schema(description = "Novo valor por parcela.") BigDecimal novoValorParcela,
        @Schema(description = "Vencimento inicial da nova agenda.") LocalDate novoVencimento,
        @Schema(description = "Numero de parcelas substitutas.") int numeroParcelas,
        @Schema(description = "Desconto aplicado.") BigDecimal desconto,
        @Schema(description = "UUID do operador (FINANCEIRO/ADMIN) que propos.") UUID propostaPor,
        @Schema(description = "Quando a proposta foi criada.") OffsetDateTime dataProposta,
        @Schema(description = "Quando a proposta expira (proposta + 7 dias).") OffsetDateTime dataExpiracao,
        @Schema(description = "Quando o tomador decidiu ou o job expirou (null se ainda PROPOSTA).")
                OffsetDateTime dataDecisao,
        @Schema(description = "UUID da agenda substituta gerada apos aceite (null se nao aceita).")
                UUID agendaSubstitutaId) {

    public static RenegociacaoResponse from(Renegociacao r) {
        return new RenegociacaoResponse(
                r.getId(),
                r.getParcelaOriginalId(),
                r.getAgendaOriginalId(),
                r.getTomadorId(),
                r.getStatus(),
                r.getStatusParcelaAnterior(),
                r.getNovoValorParcela(),
                r.getNovoVencimento(),
                r.getNumeroParcelas(),
                r.getDesconto(),
                r.getPropostaPor(),
                r.getDataProposta(),
                r.getDataExpiracao(),
                r.getDataDecisao(),
                r.getAgendaSubstitutaId());
    }
}
