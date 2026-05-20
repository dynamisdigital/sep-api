package com.dynamis.sep_api.contratos.infrastructure.persistence;

import com.dynamis.sep_api.contratos.domain.model.AceiteContrato;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AceiteContratoRepository extends JpaRepository<AceiteContrato, UUID> {

    Optional<AceiteContrato> findByVersaoId(UUID versaoId);

    boolean existsByVersaoId(UUID versaoId);
}
