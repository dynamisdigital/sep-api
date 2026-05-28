package com.dynamis.sep_api.credores.infrastructure.persistence;

import com.dynamis.sep_api.credores.domain.model.PerfilCredora;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PerfilCredoraRepository extends JpaRepository<PerfilCredora, UUID> {

    Optional<PerfilCredora> findByEmpresaCredoraId(UUID empresaCredoraId);
}
