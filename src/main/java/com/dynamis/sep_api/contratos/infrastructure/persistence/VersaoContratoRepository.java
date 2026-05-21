package com.dynamis.sep_api.contratos.infrastructure.persistence;

import com.dynamis.sep_api.contratos.domain.model.VersaoContrato;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VersaoContratoRepository extends JpaRepository<VersaoContrato, UUID> {

    @Query("select v from VersaoContrato v where v.contrato.id = :contratoId order by v.numero asc")
    List<VersaoContrato> findByContratoIdOrdenado(UUID contratoId);

    /**
     * Variante com fetch eager de {@code clausulas} via JPQL JOIN FETCH. Resolve N+1 que o {@code
     * ConsultarContratoUseCase} sofria ao tocar lazy collection por versao — 2 queries totais
     * (versoes + clausulas) em vez de 1 + N. Hibernate aceita 1 bag por query, entao precisamos
     * dessa query separada em vez de fetch combinado em {@code Contrato.findByPropostaId}
     * (MultipleBagFetchException).
     */
    @Query(
            "select distinct v from VersaoContrato v left join fetch v.clausulas where v.contrato.id = :contratoId order by v.numero asc")
    List<VersaoContrato> findByContratoIdComClausulas(UUID contratoId);

    @Query("select v from VersaoContrato v where v.contrato.id = :contratoId order by v.numero desc")
    List<VersaoContrato> findVigentes(UUID contratoId, org.springframework.data.domain.Pageable pageable);

    default Optional<VersaoContrato> findVigente(UUID contratoId) {
        return findVigentes(contratoId, org.springframework.data.domain.PageRequest.of(0, 1)).stream()
                .findFirst();
    }
}
