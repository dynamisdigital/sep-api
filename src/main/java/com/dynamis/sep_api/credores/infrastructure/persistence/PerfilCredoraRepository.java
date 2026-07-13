package com.dynamis.sep_api.credores.infrastructure.persistence;

import com.dynamis.sep_api.credores.domain.model.PerfilCredora;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PerfilCredoraRepository extends JpaRepository<PerfilCredora, UUID> {

    Optional<PerfilCredora> findByEmpresaCredoraId(UUID empresaCredoraId);

    /** Perfis das credoras candidatas ao matching em lote (Sprint 30) — sem consulta por credora. */
    List<PerfilCredora> findAllByEmpresaCredoraIdIn(Collection<UUID> empresaCredoraIds);
}
