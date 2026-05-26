package com.dynamis.sep_api.cobranca.infrastructure.persistence;

import com.dynamis.sep_api.cobranca.domain.model.WorkflowCobranca;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkflowCobrancaRepository extends JpaRepository<WorkflowCobranca, UUID> {

    List<WorkflowCobranca> findByNomeAndAtivoTrueOrderByDiaAtrasoAsc(String nome);

    Optional<WorkflowCobranca> findByNomeAndDiaAtrasoAndAtivoTrue(String nome, int diaAtraso);
}
