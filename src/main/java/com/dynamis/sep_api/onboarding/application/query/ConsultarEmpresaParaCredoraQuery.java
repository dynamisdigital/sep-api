package com.dynamis.sep_api.onboarding.application.query;

import java.util.Optional;
import java.util.UUID;

/**
 * Porta read-only publicada pelo modulo {@code onboarding} para o modulo {@code credores}
 * (Sprint 16). Expoe o dado de onboarding PJ necessario ao cadastro e a decisao de elegibilidade
 * da credora, sem expor os repositorios internos de onboarding.
 */
public interface ConsultarEmpresaParaCredoraQuery {

    Optional<EmpresaParaCredoraResumo> consultarPorId(UUID solicitacaoId);
}
