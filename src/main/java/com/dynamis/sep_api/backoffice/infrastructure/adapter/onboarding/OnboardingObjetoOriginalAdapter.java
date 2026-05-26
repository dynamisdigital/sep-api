package com.dynamis.sep_api.backoffice.infrastructure.adapter.onboarding;

import com.dynamis.sep_api.backoffice.application.dto.ObjetoOriginalResumo;
import com.dynamis.sep_api.backoffice.application.port.out.ObjetoOriginalQueryPort;
import com.dynamis.sep_api.backoffice.domain.vo.TipoEntidadeReferenciada;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.SolicitacaoOnboardingRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Strategy: resolve {@code ONBOARDING} para {@link ObjetoOriginalResumo}. Sem CPF/CNPJ no resumo
 * (LGPD) — operador acessa detalhe completo via {@code GET /api/v1/onboarding/{id}}.
 */
@Component
public class OnboardingObjetoOriginalAdapter implements ObjetoOriginalQueryPort {

    private final SolicitacaoOnboardingRepository repository;

    public OnboardingObjetoOriginalAdapter(SolicitacaoOnboardingRepository repository) {
        this.repository = repository;
    }

    @Override
    public TipoEntidadeReferenciada tipoSuportado() {
        return TipoEntidadeReferenciada.ONBOARDING;
    }

    @Override
    public Optional<ObjetoOriginalResumo> buscar(UUID entidadeId) {
        return repository
                .findById(entidadeId)
                .map(s -> new ObjetoOriginalResumo(
                        TipoEntidadeReferenciada.ONBOARDING,
                        s.getId(),
                        s.getStatus().name(),
                        "Solicitacao de onboarding"));
    }
}
