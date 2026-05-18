package com.dynamis.sep_api.credito.infrastructure.persistence;

import com.dynamis.sep_api.credito.domain.model.ScoreInterno;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ScoreInternoRepository extends JpaRepository<ScoreInterno, UUID> {

    Optional<ScoreInterno> findByPropostaId(UUID propostaId);

    void deleteByPropostaId(UUID propostaId);
}
