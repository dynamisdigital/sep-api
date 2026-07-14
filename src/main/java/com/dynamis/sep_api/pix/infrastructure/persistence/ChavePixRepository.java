package com.dynamis.sep_api.pix.infrastructure.persistence;

import com.dynamis.sep_api.pix.domain.model.ChavePix;
import com.dynamis.sep_api.pix.domain.vo.StatusChavePix;
import com.dynamis.sep_api.pix.domain.vo.TipoChavePix;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistencia da chave Pix da conta operacional (Sprint 31). Idempotencia e unicidade ativa sao
 * garantidas pelo banco (V58): UNIQUE {@code (conta_escrow_id, idempotency_key)} e UNIQUE parcial
 * {@code (conta_escrow_id, tipo, valor_hash) WHERE status='ATIVA'} — a verificacao em memoria dos
 * use cases e apenas fast-path.
 */
@Repository
public interface ChavePixRepository extends JpaRepository<ChavePix, UUID> {

    /** Replay de cadastro: mesma {@code Idempotency-Key} no escopo da conta operacional. */
    Optional<ChavePix> findByContaEscrowIdAndIdempotencyKey(UUID contaEscrowId, String idempotencyKey);

    /** Duplicata funcional: mesma chave normalizada (tipo + hash) no status dado, na conta. */
    Optional<ChavePix> findByContaEscrowIdAndTipoAndValorHashAndStatus(
            UUID contaEscrowId, TipoChavePix tipo, String valorHash, StatusChavePix status);

    /** Listagem local deterministica da conta: inclui ATIVA e INATIVA, mais recente primeiro. */
    List<ChavePix> findAllByContaEscrowIdOrderByCriadaEmDesc(UUID contaEscrowId);

    /**
     * Leitura com {@code SELECT FOR UPDATE} escopada na conta operacional para serializar remocoes
     * concorrentes (Sprint 31 Task 31.6): a segunda remocao bloqueia, re-le INATIVA e encerra como
     * replay idempotente — sem chamada dupla ao provider nem auditoria duplicada.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ChavePix c where c.id = :id and c.contaEscrowId = :contaEscrowId")
    Optional<ChavePix> findByIdAndContaEscrowIdForUpdate(UUID id, UUID contaEscrowId);
}
