package com.dynamis.sep_api.cobranca.infrastructure.persistence;

import com.dynamis.sep_api.cobranca.domain.model.ParcelaCobranca;
import com.dynamis.sep_api.cobranca.domain.vo.StatusParcela;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ParcelaCobrancaRepository extends JpaRepository<ParcelaCobranca, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ParcelaCobranca p where p.id = :id")
    Optional<ParcelaCobranca> findByIdForUpdate(UUID id);

    List<ParcelaCobranca> findByStatusAndDataVencimentoBefore(StatusParcela status, LocalDate dataLimite);

    List<ParcelaCobranca> findByAgenda_ContratoIdOrderByNumeroAsc(UUID contratoId);
}
