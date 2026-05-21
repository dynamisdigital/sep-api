package com.dynamis.sep_api.contratos.infrastructure.persistence;

import com.dynamis.sep_api.contratos.domain.model.DocumentoAssinado;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DocumentoAssinadoRepository extends JpaRepository<DocumentoAssinado, UUID> {

    Optional<DocumentoAssinado> findByEnvelopeId(UUID envelopeId);
}
