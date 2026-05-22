package com.dynamis.sep_api.cobranca.web.dto;

import com.dynamis.sep_api.cobranca.domain.vo.StatusParcela;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(
        description =
                "Recebimento registrado em parcela (Sprint 12). 'novo'=false quando idempotencia retorna o mesmo recebimento.")
public record RecebimentoResponse(
        UUID recebimentoId,
        UUID parcelaId,
        StatusParcela statusParcela,
        BigDecimal valorRecebido,
        OffsetDateTime dataRecebimento,
        String meioPagamento,
        String identificadorExterno,
        UUID movimentacaoEscrowId,
        boolean novo) {}
