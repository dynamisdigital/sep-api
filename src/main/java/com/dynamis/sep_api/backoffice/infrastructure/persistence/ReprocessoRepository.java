package com.dynamis.sep_api.backoffice.infrastructure.persistence;

import com.dynamis.sep_api.backoffice.domain.model.Reprocesso;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface ReprocessoRepository extends JpaRepository<Reprocesso, UUID> {

    long countByIdentificadorExternoAndDataDisparoAfter(String identificadorExterno, OffsetDateTime since);
}
