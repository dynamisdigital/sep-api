package com.dynamis.sep_api.credito.infrastructure.persistence;

import com.dynamis.sep_api.credito.domain.model.RegraCreditoAvaliada;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RegraCreditoAvaliadaRepository extends JpaRepository<RegraCreditoAvaliada, UUID> {

    List<RegraCreditoAvaliada> findByPropostaIdOrderByDataAvaliacaoAsc(UUID propostaId);

    void deleteByPropostaId(UUID propostaId);
}
