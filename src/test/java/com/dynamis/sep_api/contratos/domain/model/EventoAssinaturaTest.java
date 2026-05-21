package com.dynamis.sep_api.contratos.domain.model;

import com.dynamis.sep_api.contratos.domain.vo.StatusEnvelope;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventoAssinaturaTest {

    private static final OffsetDateTime AGORA = OffsetDateTime.of(2026, 5, 21, 10, 0, 0, 0, ZoneOffset.UTC);

    @Test
    void criar_camposObrigatoriosPreservados() {
        UUID envelopeId = UUID.randomUUID();

        EventoAssinatura e = EventoAssinatura.criar(envelopeId, "EVT-1", StatusEnvelope.ASSINADO, "ok", AGORA);

        assertThat(e.getEnvelopeId()).isEqualTo(envelopeId);
        assertThat(e.getIdEventoExterno()).isEqualTo("EVT-1");
        assertThat(e.getStatus()).isEqualTo(StatusEnvelope.ASSINADO);
        assertThat(e.getPayloadResumo()).isEqualTo("ok");
        assertThat(e.getDataEvento()).isEqualTo(AGORA);
    }

    @Test
    void criar_payloadResumoNulo_persisteNulo() {
        EventoAssinatura e =
                EventoAssinatura.criar(UUID.randomUUID(), "EVT-1", StatusEnvelope.VISUALIZADO, null, AGORA);

        assertThat(e.getPayloadResumo()).isNull();
    }

    @Test
    void criar_payloadExcedente_eTruncado() {
        String grande = "x".repeat(2000);

        EventoAssinatura e = EventoAssinatura.criar(UUID.randomUUID(), "EVT-1", StatusEnvelope.ENVIADO, grande, AGORA);

        assertThat(e.getPayloadResumo()).hasSize(EventoAssinatura.PAYLOAD_RESUMO_MAX);
    }

    @Test
    void criar_paramObrigatorioNulo_rejeita() {
        assertThatThrownBy(() -> EventoAssinatura.criar(null, "EVT", StatusEnvelope.ASSINADO, "x", AGORA))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("envelopeId");
        assertThatThrownBy(() -> EventoAssinatura.criar(UUID.randomUUID(), null, StatusEnvelope.ASSINADO, "x", AGORA))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("idEventoExterno");
        assertThatThrownBy(() -> EventoAssinatura.criar(UUID.randomUUID(), "EVT", null, "x", AGORA))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("status");
        assertThatThrownBy(() -> EventoAssinatura.criar(UUID.randomUUID(), "EVT", StatusEnvelope.ASSINADO, "x", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("dataEvento");
    }
}
