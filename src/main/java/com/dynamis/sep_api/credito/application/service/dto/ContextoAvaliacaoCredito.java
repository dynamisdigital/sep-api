package com.dynamis.sep_api.credito.application.service.dto;

import com.dynamis.sep_api.credito.domain.model.PropostaCredito;
import com.dynamis.sep_api.onboarding.domain.vo.StatusOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.TipoSolicitante;

import java.time.LocalDate;

/**
 * Contexto montado pelo use case {@code AvaliarPropostaUseCase} (Task 8.3) e passado ao motor de
 * regras. Inclui dados resumidos do onboarding necessarios pra avaliacao sem que o modulo {@code
 * credito} acesse repositorios internos de {@code onboarding} (regra DDD do projeto — ver
 * AGENT.md).
 *
 * <p>{@code dataNascimento} preenchido apenas pra {@link TipoSolicitante#PESSOA}; {@code
 * dataAbertura} preenchido apenas pra {@link TipoSolicitante#EMPRESA} quando disponivel
 * (pos-consulta CNPJ na Sprint 7). Regras devem retornar {@code PENDENTE} se a informacao
 * necessaria estiver ausente.
 */
public record ContextoAvaliacaoCredito(
        PropostaCredito proposta,
        TipoSolicitante tipoSolicitante,
        StatusOnboarding statusOnboarding,
        LocalDate dataNascimento,
        LocalDate dataAbertura) {

    public boolean isPessoa() {
        return tipoSolicitante == TipoSolicitante.PESSOA;
    }

    public boolean isEmpresa() {
        return tipoSolicitante == TipoSolicitante.EMPRESA;
    }
}
