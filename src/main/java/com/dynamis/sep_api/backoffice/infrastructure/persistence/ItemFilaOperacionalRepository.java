package com.dynamis.sep_api.backoffice.infrastructure.persistence;

import com.dynamis.sep_api.backoffice.domain.model.ItemFilaOperacional;
import com.dynamis.sep_api.backoffice.domain.vo.StatusItemFila;
import com.dynamis.sep_api.backoffice.domain.vo.TipoEntidadeReferenciada;
import com.dynamis.sep_api.backoffice.domain.vo.TipoItemFila;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ItemFilaOperacionalRepository
        extends JpaRepository<ItemFilaOperacional, UUID>, JpaSpecificationExecutor<ItemFilaOperacional> {

    boolean existsByTipoAndTipoEntidadeAndEntidadeIdAndStatusIn(
            TipoItemFila tipo, TipoEntidadeReferenciada tipoEntidade, UUID entidadeId, Collection<StatusItemFila> ativos);

    List<ItemFilaOperacional> findByTipoEntidadeAndEntidadeIdOrderByDataAberturaDesc(
            TipoEntidadeReferenciada tipoEntidade, UUID entidadeId);

    /**
     * Lock pessimista pra serializar transicoes concorrentes (assumir / resolver / ignorar) —
     * padrao Sprint 13 Renegociacao (fix code review Task 14.3).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from ItemFilaOperacional i where i.id = :id")
    Optional<ItemFilaOperacional> findByIdForUpdate(UUID id);
}
