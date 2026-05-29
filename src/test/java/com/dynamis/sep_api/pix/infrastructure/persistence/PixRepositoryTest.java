package com.dynamis.sep_api.pix.infrastructure.persistence;

import com.dynamis.sep_api.pix.domain.model.PixRecebimento;
import com.dynamis.sep_api.pix.domain.model.PixTransferencia;
import com.dynamis.sep_api.pix.domain.model.PixWebhookEvent;
import com.dynamis.sep_api.pix.domain.vo.TipoPixWebhookEvent;
import com.dynamis.sep_api.shared.audit.AuditorAwareImpl;
import com.dynamis.sep_api.shared.audit.JpaAuditingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Persistencia e constraints de idempotencia do modulo Pix (Sprint 19 Task 19.2). Valida o que o
 * dominio nao garante sozinho: UNIQUE de idempotency_key, UNIQUE parcial de end_to_end_id e UNIQUE
 * composto (provider, event_id).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, AuditorAwareImpl.class})
@ActiveProfiles("dev")
class PixRepositoryTest {

    private static final BigDecimal VALOR = new BigDecimal("150.00");

    @Autowired
    private PixTransferenciaRepository transferenciaRepository;

    @Autowired
    private PixRecebimentoRepository recebimentoRepository;

    @Autowired
    private PixWebhookEventRepository webhookEventRepository;

    @Test
    void transferenciaPersisteERecuperaPorIdempotencyKey() {
        PixTransferencia t = PixTransferencia.criar(VALOR, "pagamento", "idem-1", "corr-1");
        transferenciaRepository.saveAndFlush(t);

        PixTransferencia achado = transferenciaRepository.findByIdempotencyKey("idem-1").orElseThrow();
        assertThat(achado.getId()).isEqualTo(t.getId());
        assertThat(transferenciaRepository.findByIdempotencyKey("nao-existe")).isEmpty();
    }

    @Test
    void transferenciaIdempotencyKeyDuplicadaFalha() {
        transferenciaRepository.saveAndFlush(PixTransferencia.criar(VALOR, "x", "idem-dup", "corr-1"));

        PixTransferencia outra = PixTransferencia.criar(VALOR, "y", "idem-dup", "corr-2");
        assertThatThrownBy(() -> transferenciaRepository.saveAndFlush(outra))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void recebimentoPersisteERecuperaPorEndToEndId() {
        PixRecebimento r = PixRecebimento.registrar("E2E-1", VALOR, OffsetDateTime.now(), "corr-1");
        recebimentoRepository.saveAndFlush(r);

        assertThat(recebimentoRepository.findByEndToEndId("E2E-1").orElseThrow().getId()).isEqualTo(r.getId());
    }

    @Test
    void recebimentoEndToEndIdDuplicadoFalha() {
        recebimentoRepository.saveAndFlush(PixRecebimento.registrar("E2E-dup", VALOR, OffsetDateTime.now(), "corr-1"));

        PixRecebimento outro = PixRecebimento.registrar("E2E-dup", VALOR, OffsetDateTime.now(), "corr-2");
        assertThatThrownBy(() -> recebimentoRepository.saveAndFlush(outro))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void recebimentoPermiteMultiplosEndToEndIdNulos() {
        recebimentoRepository.saveAndFlush(PixRecebimento.registrar(null, VALOR, OffsetDateTime.now(), "corr-1"));
        recebimentoRepository.saveAndFlush(PixRecebimento.registrar(null, VALOR, OffsetDateTime.now(), "corr-2"));

        assertThat(recebimentoRepository.count()).isEqualTo(2);
    }

    @Test
    void webhookEventPersisteEDetectaDuplicado() {
        PixWebhookEvent e = PixWebhookEvent.receber("celcoin", "evt-1", TipoPixWebhookEvent.RECEBIMENTO_PIX, "hash-1");
        webhookEventRepository.saveAndFlush(e);

        assertThat(webhookEventRepository.existsByProviderAndEventId("celcoin", "evt-1")).isTrue();
        assertThat(webhookEventRepository.existsByProviderAndEventId("celcoin", "evt-2")).isFalse();
        assertThat(webhookEventRepository.findByProviderAndEventId("celcoin", "evt-1").orElseThrow().getId())
                .isEqualTo(e.getId());
    }

    @Test
    void webhookEventProviderEventIdDuplicadoFalha() {
        webhookEventRepository.saveAndFlush(
                PixWebhookEvent.receber("celcoin", "evt-dup", TipoPixWebhookEvent.RECEBIMENTO_PIX, "hash-1"));

        PixWebhookEvent outro =
                PixWebhookEvent.receber("celcoin", "evt-dup", TipoPixWebhookEvent.STATUS_TRANSFERENCIA, "hash-2");
        assertThatThrownBy(() -> webhookEventRepository.saveAndFlush(outro))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void webhookEventMesmoEventIdProviderDiferentePersiste() {
        webhookEventRepository.saveAndFlush(
                PixWebhookEvent.receber("celcoin", "evt-x", TipoPixWebhookEvent.RECEBIMENTO_PIX, "hash-1"));
        webhookEventRepository.saveAndFlush(
                PixWebhookEvent.receber("outro", "evt-x", TipoPixWebhookEvent.RECEBIMENTO_PIX, "hash-2"));

        assertThat(webhookEventRepository.count()).isEqualTo(2);
    }
}
