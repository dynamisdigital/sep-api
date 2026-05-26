package com.dynamis.sep_api.cobranca.web.dto;

import com.dynamis.sep_api.cobranca.domain.vo.StatusParcela;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Linha de inadimplencia retornada pra backoffice/financeiro (Sprint 13 Task 13.7). Carrega
 * identificadores + status + dias de atraso pra triagem.
 */
@Schema(description = "Parcela em atraso ou inadimplente — visao financeiro/admin.")
public record InadimplenciaResponse(
        @Schema(description = "UUID da parcela.") UUID parcelaId,
        @Schema(description = "UUID da agenda.") UUID agendaId,
        @Schema(description = "UUID do contrato.") UUID contratoId,
        @Schema(description = "UUID do tomador.") UUID tomadorId,
        @Schema(description = "Numero da parcela no contrato.") int numeroParcela,
        @Schema(description = "Status atual.") StatusParcela status,
        @Schema(description = "Data de vencimento original.") LocalDate dataVencimento,
        @Schema(description = "Dias de atraso ate hoje.") int diasAtraso,
        @Schema(description = "Valor original da parcela (sem mora/multa).") BigDecimal valorOriginal) {}
