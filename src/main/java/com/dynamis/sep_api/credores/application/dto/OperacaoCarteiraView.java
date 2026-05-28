package com.dynamis.sep_api.credores.application.dto;

import com.dynamis.sep_api.credores.application.port.out.CarteiraCobrancaResumo;
import com.dynamis.sep_api.credores.domain.vo.StatusOperacaoFinanciada;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Projecao read-only de uma operacao da carteira credora, enriquecida com dados do snapshot da
 * oportunidade e com o resumo de cobranca. {@code valor}/{@code prazoMeses}/{@code taxaJurosMensal}
 * nulos quando a operacao nao referencia oportunidade; {@code contratoStatus}/{@code cobranca}
 * nulos quando a leitura cross-module nao retorna dados.
 */
public record OperacaoCarteiraView(
        UUID id,
        UUID contratoId,
        UUID oportunidadeId,
        StatusOperacaoFinanciada status,
        String justificativa,
        BigDecimal valor,
        Integer prazoMeses,
        BigDecimal taxaJurosMensal,
        String contratoStatus,
        CarteiraCobrancaResumo cobranca,
        OffsetDateTime dataCriacao) {}
