package com.dynamis.sep_api.governanca.infrastructure.persistence;

import com.dynamis.sep_api.governanca.domain.model.VersaoParametroOperacional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface VersaoParametroOperacionalRepository extends JpaRepository<VersaoParametroOperacional, UUID> {

    List<VersaoParametroOperacional> findByParametroIdOrderByVersaoDesc(UUID parametroId);
}
