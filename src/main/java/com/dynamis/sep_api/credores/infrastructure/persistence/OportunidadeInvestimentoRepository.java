package com.dynamis.sep_api.credores.infrastructure.persistence;

import com.dynamis.sep_api.credores.domain.model.OportunidadeInvestimento;
import com.dynamis.sep_api.credores.domain.vo.StatusOportunidade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OportunidadeInvestimentoRepository extends JpaRepository<OportunidadeInvestimento, UUID> {

    Optional<OportunidadeInvestimento> findByPropostaId(UUID propostaId);

    boolean existsByPropostaId(UUID propostaId);

    List<OportunidadeInvestimento> findByStatusOrderByDataCriacaoDesc(StatusOportunidade status);
}
