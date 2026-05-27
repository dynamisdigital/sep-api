package com.dynamis.sep_api.onboarding.infrastructure.persistence;

import com.dynamis.sep_api.onboarding.domain.model.SolicitacaoOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.StatusOnboarding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SolicitacaoOnboardingRepository extends JpaRepository<SolicitacaoOnboarding, UUID> {

    boolean existsByDocumentoAndStatusIn(String documento, Collection<StatusOnboarding> statuses);

    Optional<SolicitacaoOnboarding> findByIdAndUsuarioId(UUID id, UUID usuarioId);

    Optional<SolicitacaoOnboarding> findByIdVerificacaoExterna(String idVerificacaoExterna);
}
