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

    /**
     * @deprecated Sprint 7: usar {@link #existsByDocumentoAndStatusIn(String, Collection)} que
     *     cobre CPF e CNPJ. Mantido para Sprint 6 ate refactor completo dos callers.
     */
    @Deprecated
    boolean existsByCpfAndStatusIn(String cpf, Collection<StatusOnboarding> statuses);

    boolean existsByDocumentoAndStatusIn(String documento, Collection<StatusOnboarding> statuses);

    Optional<SolicitacaoOnboarding> findByIdAndUsuarioId(UUID id, UUID usuarioId);

    Optional<SolicitacaoOnboarding> findByIdVerificacaoExterna(String idVerificacaoExterna);
}
