package com.dynamis.sep_api.credores.application.port.out;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Dados minimos de uma proposta elegivel para virar oportunidade de investimento, expostos pelo
 * modulo {@code credito} ao modulo {@code credores} (Sprint 17). Sem dados sensiveis do tomador.
 *
 * <p>{@code contratoId} preenchido quando ja existe contrato formalizado para a proposta.
 */
public record PropostaElegivelView(
        UUID propostaId, UUID contratoId, BigDecimal valor, int prazoMeses, BigDecimal taxaJurosMensal) {}
