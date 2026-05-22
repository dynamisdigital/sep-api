package com.dynamis.sep_api.cobranca.application.dto;

import com.dynamis.sep_api.cobranca.domain.vo.StatusParcela;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Query de consulta de parcelas (Sprint 12 - Task 12.5).
 * Todos os campos sao opcionais (null = sem filtro).
 */
public record ConsultarParcelasQuery(
        UUID contratoId, StatusParcela status, LocalDate vencimentoInicio, LocalDate vencimentoFim) {}
