package com.dynamis.sep_api.credores.infrastructure.persistence;

import com.dynamis.sep_api.credores.domain.model.AporteCredora;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AporteCredoraRepository extends JpaRepository<AporteCredora, UUID> {

    Optional<AporteCredora> findByOperacaoIdAndIdempotencyKey(UUID operacaoId, String idempotencyKey);

    List<AporteCredora> findByOperacaoIdOrderByDataCriacaoDesc(UUID operacaoId);

    /**
     * Leitura com {@code SELECT FOR UPDATE} para serializar reconciliacoes concorrentes do mesmo
     * aporte (Sprint 29): a segunda chamada bloqueia, re-le o estado terminal e cai no replay
     * no-op — sem transicao nem auditoria duplicada.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AporteCredora a where a.referenciaEscrow = :referenciaEscrow")
    Optional<AporteCredora> findByReferenciaEscrowForUpdate(String referenciaEscrow);
}
