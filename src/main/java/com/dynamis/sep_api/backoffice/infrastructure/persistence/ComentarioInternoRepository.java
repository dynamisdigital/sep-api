package com.dynamis.sep_api.backoffice.infrastructure.persistence;

import com.dynamis.sep_api.backoffice.domain.model.ComentarioInterno;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ComentarioInternoRepository extends JpaRepository<ComentarioInterno, UUID> {

    List<ComentarioInterno> findByItemIdOrderByDataCriacaoAsc(UUID itemId);
}
