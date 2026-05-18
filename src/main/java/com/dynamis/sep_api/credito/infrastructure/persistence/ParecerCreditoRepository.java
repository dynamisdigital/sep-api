package com.dynamis.sep_api.credito.infrastructure.persistence;

import com.dynamis.sep_api.credito.domain.model.ParecerCredito;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ParecerCreditoRepository extends JpaRepository<ParecerCredito, UUID> {

    List<ParecerCredito> findByPropostaIdOrderByVersaoAsc(UUID propostaId);

    Optional<ParecerCredito> findTopByPropostaIdOrderByVersaoDesc(UUID propostaId);

    long countByPropostaId(UUID propostaId);
}
