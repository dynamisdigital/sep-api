package com.dynamis.sep_api.credores.infrastructure.persistence;

import com.dynamis.sep_api.credores.domain.model.InteresseCredora;
import com.dynamis.sep_api.credores.domain.vo.StatusInteresseCredora;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InteresseCredoraRepository extends JpaRepository<InteresseCredora, UUID> {

    boolean existsByEmpresaCredoraIdAndOportunidadeIdAndStatus(
            UUID empresaCredoraId, UUID oportunidadeId, StatusInteresseCredora status);

    Optional<InteresseCredora> findByEmpresaCredoraIdAndOportunidadeIdAndStatus(
            UUID empresaCredoraId, UUID oportunidadeId, StatusInteresseCredora status);

    List<InteresseCredora> findByEmpresaCredoraIdAndStatusOrderByDataCriacaoDesc(
            UUID empresaCredoraId, StatusInteresseCredora status);
}
