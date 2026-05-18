package com.dynamis.sep_api.credito.infrastructure.persistence;

import com.dynamis.sep_api.credito.domain.model.PropostaCredito;
import com.dynamis.sep_api.credito.domain.vo.StatusProposta;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PropostaCreditoRepository extends JpaRepository<PropostaCredito, UUID> {

    Optional<PropostaCredito> findByIdAndTomadorId(UUID id, UUID tomadorId);

    Page<PropostaCredito> findByTomadorId(UUID tomadorId, Pageable pageable);

    Page<PropostaCredito> findByStatus(StatusProposta status, Pageable pageable);

    Page<PropostaCredito> findByStatusInAndTomadorId(
            Collection<StatusProposta> statuses, UUID tomadorId, Pageable pageable);

    Page<PropostaCredito> findByStatusIn(Collection<StatusProposta> statuses, Pageable pageable);
}
