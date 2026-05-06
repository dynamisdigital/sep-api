package com.dynamis.sep_api.shared.infrastructure.persistence;

import com.dynamis.sep_api.shared.domain.model.WebhookEventLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WebhookEventLogRepository extends JpaRepository<WebhookEventLog, UUID> {

    boolean existsByIdempotencyKey(String idempotencyKey);

    Optional<WebhookEventLog> findByIdempotencyKey(String idempotencyKey);
}
