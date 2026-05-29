package com.dynamis.sep_api.pix.infrastructure.persistence;

import com.dynamis.sep_api.pix.domain.model.PixWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PixWebhookEventRepository extends JpaRepository<PixWebhookEvent, UUID> {

    boolean existsByProviderAndEventId(String provider, String eventId);

    Optional<PixWebhookEvent> findByProviderAndEventId(String provider, String eventId);
}
