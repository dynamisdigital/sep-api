package com.dynamis.sep_api.governanca.infrastructure.persistence;

import com.dynamis.sep_api.governanca.domain.model.ParametroOperacional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ParametroOperacionalRepository extends JpaRepository<ParametroOperacional, UUID> {

    Optional<ParametroOperacional> findByChave(String chave);
}
