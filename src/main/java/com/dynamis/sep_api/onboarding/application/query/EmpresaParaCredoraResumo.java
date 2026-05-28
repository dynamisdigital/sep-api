package com.dynamis.sep_api.onboarding.application.query;

import com.dynamis.sep_api.onboarding.domain.vo.StatusOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.TipoSolicitante;

import java.util.UUID;

/**
 * Resumo read-only de uma {@code SolicitacaoOnboarding} PJ consumido pelo modulo {@code credores}
 * (Sprint 16, Task 4). Mantem {@code credores} desacoplado dos repositorios internos de {@code
 * onboarding} (regra DDD do projeto — ver AGENT.md).
 *
 * <p>{@code cnpj} e {@code razaoSocial} sao preenchidos a partir do {@code KybEmpresa} vinculado;
 * ficam nulos quando a solicitacao nao e do tipo {@link TipoSolicitante#EMPRESA} ou o KYB ainda
 * nao foi registrado.
 */
public record EmpresaParaCredoraResumo(
        UUID solicitacaoId,
        UUID usuarioId,
        TipoSolicitante tipoSolicitante,
        StatusOnboarding status,
        String cnpj,
        String razaoSocial) {}
