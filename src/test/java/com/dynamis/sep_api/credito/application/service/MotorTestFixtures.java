package com.dynamis.sep_api.credito.application.service;

import com.dynamis.sep_api.credito.application.service.dto.ContextoAvaliacaoCredito;
import com.dynamis.sep_api.credito.domain.model.PropostaCredito;
import com.dynamis.sep_api.credito.domain.vo.Money;
import com.dynamis.sep_api.credito.domain.vo.TipoOperacao;
import com.dynamis.sep_api.onboarding.domain.vo.StatusOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.TipoSolicitante;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Fixtures compartilhadas pelos testes do motor de regras de credito (Sprint 8 Task 8.2). */
public final class MotorTestFixtures {

    private MotorTestFixtures() {}

    public static CreditoMotorProperties propertiesDefault() {
        return new CreditoMotorProperties(
                1000, 50, 20, 700, 400, 18, 6, new BigDecimal("50000.00"), new BigDecimal("200000.00"), 12, 24);
    }

    public static PropostaCredito propostaPf(BigDecimal valor, int prazoMeses) {
        return PropostaCredito.criar(
                UUID.randomUUID(), UUID.randomUUID(), TipoOperacao.OUTROS, new Money(valor, "BRL"), prazoMeses);
    }

    public static PropostaCredito propostaPj(BigDecimal valor, int prazoMeses) {
        return PropostaCredito.criar(
                UUID.randomUUID(), UUID.randomUUID(), TipoOperacao.CAPITAL_GIRO, new Money(valor, "BRL"), prazoMeses);
    }

    public static ContextoAvaliacaoCredito contextoPfOk(PropostaCredito proposta) {
        return new ContextoAvaliacaoCredito(
                proposta,
                TipoSolicitante.PESSOA,
                StatusOnboarding.APROVADO_FINAL,
                LocalDate.now().minusYears(30),
                null);
    }

    public static ContextoAvaliacaoCredito contextoPjOk(PropostaCredito proposta) {
        return new ContextoAvaliacaoCredito(
                proposta,
                TipoSolicitante.EMPRESA,
                StatusOnboarding.APROVADO_FINAL,
                null,
                LocalDate.now().minusYears(2));
    }
}
