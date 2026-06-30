package com.dynamis.sep_api.shared.application.usecase;

import com.dynamis.sep_api.shared.domain.model.WebhookEventLog;
import com.dynamis.sep_api.shared.exception.ValidacaoException;
import com.dynamis.sep_api.shared.infrastructure.persistence.WebhookEventLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registra um webhook recebido como evento {@code PENDENTE} no outbox stub. Garante idempotencia
 * por {@code Idempotency-Key}: chamadas repetidas com a mesma chave nao geram novos registros.
 */
@Service
public class RegistrarWebhookEventUseCase {

    private static final Logger log = LoggerFactory.getLogger(RegistrarWebhookEventUseCase.class);
    private static final String CODIGO_VALIDACAO = "WHK-400-001";

    private final WebhookEventLogRepository repository;

    public RegistrarWebhookEventUseCase(WebhookEventLogRepository repository) {
        this.repository = repository;
    }

    /**
     * @return {@code true} quando o evento foi gravado; {@code false} quando duplicado idempotente.
     */
    @Transactional
    public boolean executar(String provider, String event, String idempotencyKey, String signature, String payload) {
        if (isBlank(provider) || isBlank(event) || isBlank(idempotencyKey) || isBlank(payload)) {
            throw new ValidacaoException(
                    CODIGO_VALIDACAO, "provider, event, idempotencyKey e payload sao obrigatorios");
        }

        if (repository.existsByIdempotencyKey(idempotencyKey)) {
            log.info("Webhook duplicado ignorado (idempotency key ja registrada)");
            return false;
        }

        WebhookEventLog event_log = WebhookEventLog.registrar(provider, event, idempotencyKey, signature, payload);
        try {
            repository.save(event_log);
            return true;
        } catch (DataIntegrityViolationException ex) {
            // corrida concorrente: outra thread inseriu antes; tratar como duplicado
            return false;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
