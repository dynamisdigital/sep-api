package com.dynamis.sep_api.escrow.application.port.out.dto;

import com.dynamis.sep_api.escrow.domain.vo.StatusContaEscrow;

/**
 * Resposta do provider para criacao/consulta de conta escrow: id externo + status. Reaproveita o
 * VO de dominio {@link StatusContaEscrow} (dependencia para dentro — application conhece domain).
 */
public record RespostaContaEscrow(String externalId, StatusContaEscrow status) {}
