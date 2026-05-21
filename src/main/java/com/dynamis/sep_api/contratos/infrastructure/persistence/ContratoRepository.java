package com.dynamis.sep_api.contratos.infrastructure.persistence;

import com.dynamis.sep_api.contratos.domain.model.Contrato;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface ContratoRepository extends JpaRepository<Contrato, UUID> {

    Optional<Contrato> findByPropostaId(UUID propostaId);

    boolean existsByPropostaId(UUID propostaId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Contrato c where c.id = :id")
    Optional<Contrato> findByIdForUpdate(UUID id);
}
