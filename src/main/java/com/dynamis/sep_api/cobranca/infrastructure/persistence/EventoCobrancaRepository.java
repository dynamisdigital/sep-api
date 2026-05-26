package com.dynamis.sep_api.cobranca.infrastructure.persistence;

import com.dynamis.sep_api.cobranca.domain.model.EventoCobranca;
import com.dynamis.sep_api.cobranca.domain.vo.CanalNotificacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EventoCobrancaRepository extends JpaRepository<EventoCobranca, UUID> {

    List<EventoCobranca> findByParcelaIdOrderByDataEventoAsc(UUID parcelaId);

    /**
     * Guard de idempotencia em memoria (Task 13.4): se a UNIQUE da migration falhar primeiro, o
     * use case usa esse boolean pra evitar pre-construir o registro. Confronta combinacao
     * (parcela, dias_atraso, canal, template).
     */
    boolean existsByParcelaIdAndDiasAtrasoAndCanalAndTemplate(
            UUID parcelaId, Integer diasAtraso, CanalNotificacao canal, String template);
}
