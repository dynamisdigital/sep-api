package com.dynamis.sep_api.backoffice.domain.event;

import com.dynamis.sep_api.backoffice.domain.model.Reprocesso;
import com.dynamis.sep_api.backoffice.domain.vo.StatusReprocesso;
import com.dynamis.sep_api.backoffice.domain.vo.TipoChamadaProvider;

import java.util.UUID;

/**
 * Publicado pelos use cases de reprocesso (Sprint 14 Task 14.4) apos persistir o registro
 * {@code Reprocesso}. Consumido por audit (Task 14.8). Expandido em Task 14.8 fix manual pra
 * carregar todos os campos auditaveis (itemId, status, tipoChamada).
 */
public record ReprocessoDisparadoEvent(
        UUID reprocessoId,
        Reprocesso.Tipo tipo,
        TipoChamadaProvider tipoChamada,
        String identificadorExterno,
        StatusReprocesso status,
        UUID itemId,
        UUID disparadoPor) {}
