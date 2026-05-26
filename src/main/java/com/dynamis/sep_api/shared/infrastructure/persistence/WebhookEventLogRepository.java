package com.dynamis.sep_api.shared.infrastructure.persistence;

import com.dynamis.sep_api.shared.domain.model.WebhookEventLog;
import com.dynamis.sep_api.shared.domain.model.WebhookEventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WebhookEventLogRepository extends JpaRepository<WebhookEventLog, UUID> {

    boolean existsByIdempotencyKey(String idempotencyKey);

    Optional<WebhookEventLog> findByIdempotencyKey(String idempotencyKey);

    /**
     * Consumido pelo {@code WebhookFalhouListener} (Sprint 14 Task 14.2) pra detectar entradas
     * da Outbox que falharam ou ficaram pendentes alem do threshold operacional (default 1h).
     * Usa {@code dataModificacao} (e nao {@code dataCriacao}) pra alinhar com {@code Proposta} e
     * {@code Contrato}: o que importa eh quando o estado ficou parado, nao quando o registro
     * surgiu.
     */
    List<WebhookEventLog> findByStatusInAndDataModificacaoBefore(
            Collection<WebhookEventStatus> statuses, OffsetDateTime corte);
}
