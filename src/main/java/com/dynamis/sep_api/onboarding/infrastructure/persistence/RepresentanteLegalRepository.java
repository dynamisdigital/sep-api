package com.dynamis.sep_api.onboarding.infrastructure.persistence;

import com.dynamis.sep_api.onboarding.domain.model.RepresentanteLegal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RepresentanteLegalRepository extends JpaRepository<RepresentanteLegal, UUID> {

    List<RepresentanteLegal> findByKybEmpresaId(UUID kybEmpresaId);
}
