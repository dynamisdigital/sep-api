package com.dynamis.sep_api.backoffice.infrastructure.persistence;

import com.dynamis.sep_api.backoffice.domain.model.ItemFilaOperacional;
import com.dynamis.sep_api.backoffice.domain.vo.StatusItemFila;
import com.dynamis.sep_api.backoffice.domain.vo.TipoEntidadeReferenciada;
import com.dynamis.sep_api.backoffice.domain.vo.TipoItemFila;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ItemFilaOperacionalRepository
        extends JpaRepository<ItemFilaOperacional, UUID>, JpaSpecificationExecutor<ItemFilaOperacional> {

    boolean existsByTipoAndTipoEntidadeAndEntidadeIdAndStatusIn(
            TipoItemFila tipo, TipoEntidadeReferenciada tipoEntidade, UUID entidadeId, Collection<StatusItemFila> ativos);

    List<ItemFilaOperacional> findByTipoEntidadeAndEntidadeIdOrderByDataAberturaDesc(
            TipoEntidadeReferenciada tipoEntidade, UUID entidadeId);
}
