package com.dynamis.sep_api.credores.infrastructure.persistence;

import com.dynamis.sep_api.credores.domain.model.EmpresaCredora;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmpresaCredoraRepository extends JpaRepository<EmpresaCredora, UUID> {

    Optional<EmpresaCredora> findByUsuarioId(UUID usuarioId);

    Optional<EmpresaCredora> findByOnboardingId(UUID onboardingId);

    boolean existsByCnpj(String cnpj);

    boolean existsByUsuarioId(UUID usuarioId);

    boolean existsByOnboardingId(UUID onboardingId);
}
