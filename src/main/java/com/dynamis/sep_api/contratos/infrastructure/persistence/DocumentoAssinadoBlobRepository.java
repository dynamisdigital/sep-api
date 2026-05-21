package com.dynamis.sep_api.contratos.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DocumentoAssinadoBlobRepository extends JpaRepository<DocumentoAssinadoBlob, UUID> {}
