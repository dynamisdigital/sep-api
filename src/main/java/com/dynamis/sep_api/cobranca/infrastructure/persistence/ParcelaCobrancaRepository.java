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

    /**
     * Sprint 13 fix review manual Task 13.9: variante com {@code JOIN FETCH agenda} pra resolver
     * lazy load fora de transacao (usado pelo {@code EscaladorCobrancaJob}). Evita {@code
     * @Transactional} outer que segurava locks do batch inteiro.
     */
    @Query(
            "select p from ParcelaCobranca p join fetch p.agenda where p.status = :status and p.dataVencimento < :dataLimite")
    List<ParcelaCobranca> findComAgendaByStatusAndDataVencimentoBefore(
            @org.springframework.data.repository.query.Param("status") StatusParcela status,
            @org.springframework.data.repository.query.Param("dataLimite") LocalDate dataLimite);

    List<ParcelaCobranca> findByAgenda_ContratoIdOrderByNumeroAsc(UUID contratoId);

    /**
     * Sprint 13 Task 13.7: listagem operacional pra GET /inadimplencia — filtra por conjunto
     * de status (ATRASADA/INADIMPLENTE) ordenando por data de vencimento.
     */
    List<ParcelaCobranca> findByStatusInOrderByDataVencimentoAsc(java.util.Collection<StatusParcela> statuses);

    /**
     * Agregacao para dashboard do backoffice (Sprint 14 Task 14.5): conta parcelas inadimplentes
     * e soma o valor total devido (principal + juros + multa + encargos).
     */
    @Query("select count(p), coalesce(sum(p.principal + p.juros + p.multa + p.encargos), 0) "
            + "from ParcelaCobranca p where p.status = com.dynamis.sep_api.cobranca.domain.vo.StatusParcela.INADIMPLENTE")
    Object[] resumoInadimplencia();
}
