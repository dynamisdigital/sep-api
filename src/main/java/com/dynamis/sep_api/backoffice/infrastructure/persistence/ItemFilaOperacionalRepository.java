package com.dynamis.sep_api.backoffice.infrastructure.persistence;

import com.dynamis.sep_api.backoffice.domain.model.ItemFilaOperacional;
import com.dynamis.sep_api.backoffice.domain.vo.PrioridadeItem;
import com.dynamis.sep_api.backoffice.domain.vo.StatusItemFila;
import com.dynamis.sep_api.backoffice.domain.vo.TipoEntidadeReferenciada;
import com.dynamis.sep_api.backoffice.domain.vo.TipoItemFila;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
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

    /** Agregacao para dashboard (Sprint 14 Task 14.5) — contadores por tipo. */
    @Query("select new com.dynamis.sep_api.backoffice.application.dto.ContadorPorTipo(i.tipo, count(i)) "
            + "from ItemFilaOperacional i group by i.tipo")
    List<com.dynamis.sep_api.backoffice.application.dto.ContadorPorTipo> contarPorTipo();

    @Query("select new com.dynamis.sep_api.backoffice.application.dto.ContadorPorPrioridade(i.prioridade, count(i)) "
            + "from ItemFilaOperacional i group by i.prioridade")
    List<com.dynamis.sep_api.backoffice.application.dto.ContadorPorPrioridade> contarPorPrioridade();

    @Query("select new com.dynamis.sep_api.backoffice.application.dto.ContadorPorStatus(i.status, count(i)) "
            + "from ItemFilaOperacional i group by i.status")
    List<com.dynamis.sep_api.backoffice.application.dto.ContadorPorStatus> contarPorStatus();

    /**
     * Tempo medio (em segundos) entre {@code dataAbertura} e {@code dataResolucao} dos itens
     * resolvidos desde {@code corte}. Query nativa porque HQL nao suporta
     * {@code extract(second from interval)} portavelmente.
     */
    @Query(
            value = "select avg(extract(epoch from (data_resolucao - data_abertura))) "
                    + "from item_fila_operacional "
                    + "where status = 'RESOLVIDO' and data_resolucao >= :corte",
            nativeQuery = true)
    Double tempoMedioResolucaoSegundosDesde(OffsetDateTime corte);

    long countByPrioridadeAndStatusInAndDataAberturaBefore(
            PrioridadeItem prioridade, Collection<StatusItemFila> statuses, OffsetDateTime corte);
}
