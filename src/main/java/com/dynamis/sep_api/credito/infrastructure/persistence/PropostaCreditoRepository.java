package com.dynamis.sep_api.credito.infrastructure.persistence;

import com.dynamis.sep_api.credito.domain.model.PropostaCredito;
import com.dynamis.sep_api.credito.domain.vo.StatusProposta;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
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

    /**
     * Carrega a proposta com lock pessimista {@code SELECT ... FOR UPDATE} (Sprint 8 Task 8.4 —
     * fix code review). Usado pelo {@code RegistrarParecerUseCase} pra serializar pareceres
     * concorrentes na mesma proposta — evita 2 requests calcularem a mesma versao e baterem na
     * unique {@code uq_parecer_credito_versao}.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PropostaCredito p where p.id = :id")
    Optional<PropostaCredito> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Consumido pelo {@code PropostaPendenciaListener} (Sprint 14 Task 14.2) pra detectar
     * propostas paradas em analise alem do threshold operacional (default 24h).
     */
    List<PropostaCredito> findByStatusAndDataModificacaoBefore(StatusProposta status, OffsetDateTime corte);

    /** Agregacao para dashboard do backoffice (Sprint 14 Task 14.5). */
    @Query("select p.status as status, count(p) as total from PropostaCredito p group by p.status")
    List<StatusContagemView> contarPorStatus();

    interface StatusContagemView {
        StatusProposta getStatus();

        long getTotal();
    }
}
