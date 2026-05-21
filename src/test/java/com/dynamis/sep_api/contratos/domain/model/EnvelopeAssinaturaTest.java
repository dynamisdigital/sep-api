package com.dynamis.sep_api.contratos.domain.model;

import com.dynamis.sep_api.contratos.domain.exception.ContratoEstadoInvalidoException;
import com.dynamis.sep_api.contratos.domain.vo.StatusEnvelope;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnvelopeAssinaturaTest {

    private static final String HASH = "a".repeat(64);
    private static final OffsetDateTime T0 = OffsetDateTime.of(2026, 5, 21, 10, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime T1 = T0.plusMinutes(5);
    private static final OffsetDateTime T2 = T0.plusMinutes(10);

    @Test
    void criar_nasceEnviado() {
        EnvelopeAssinatura e = novo();

        assertThat(e.getStatus()).isEqualTo(StatusEnvelope.ENVIADO);
        assertThat(e.getIdEnvelopeExterno()).isEqualTo("EXT-1");
        assertThat(e.getDataEnvio()).isEqualTo(T0);
        assertThat(e.getDataAtualizacaoProvider()).isEqualTo(T0);
    }

    @Test
    void criar_hashInvalido_rejeita() {
        assertThatThrownBy(() -> EnvelopeAssinatura.criar(
                        UUID.randomUUID(), UUID.randomUUID(), "clicksign", "EXT-1", "IDEMP-1", "naohex", T0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hashPdfEnviado");
    }

    @Test
    void criar_paramObrigatorioNulo_rejeita() {
        assertThatThrownBy(() -> EnvelopeAssinatura.criar(null, UUID.randomUUID(), "p", "X", "K", HASH, T0))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(
                        () -> EnvelopeAssinatura.criar(UUID.randomUUID(), UUID.randomUUID(), "p", null, "K", HASH, T0))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("idEnvelopeExterno");
    }

    @Test
    void marcarVisualizado_transicionaEAtualizaData() {
        EnvelopeAssinatura e = novo();

        e.marcarVisualizado(T1);
        assertThat(e.getStatus()).isEqualTo(StatusEnvelope.VISUALIZADO);
        assertThat(e.getDataAtualizacaoProvider()).isEqualTo(T1);

        // Idempotente: segunda visualizacao mantem estado mas atualiza data
        e.marcarVisualizado(T2);
        assertThat(e.getStatus()).isEqualTo(StatusEnvelope.VISUALIZADO);
        assertThat(e.getDataAtualizacaoProvider()).isEqualTo(T2);
    }

    @Test
    void marcarVisualizado_emEstadoFinal_noop() {
        EnvelopeAssinatura e = novo();
        e.marcarAssinado(T1);
        OffsetDateTime dataAntes = e.getDataAtualizacaoProvider();

        e.marcarVisualizado(T2);

        assertThat(e.getStatus()).isEqualTo(StatusEnvelope.ASSINADO);
        assertThat(e.getDataAtualizacaoProvider()).isEqualTo(dataAntes); // data nao atualiza em final
    }

    @Test
    void marcarAssinado_transicionaDeEnviadoOuVisualizado() {
        EnvelopeAssinatura e = novo();

        e.marcarAssinado(T1);
        assertThat(e.getStatus()).isEqualTo(StatusEnvelope.ASSINADO);
        assertThat(e.getDataAtualizacaoProvider()).isEqualTo(T1);
    }

    @Test
    void marcarAssinado_idempotenteAtualizaData() {
        EnvelopeAssinatura e = novo();
        e.marcarAssinado(T1);

        e.marcarAssinado(T2);

        assertThat(e.getStatus()).isEqualTo(StatusEnvelope.ASSINADO);
        assertThat(e.getDataAtualizacaoProvider()).isEqualTo(T2);
    }

    @Test
    void marcarAssinado_aposRecusado_rejeita() {
        EnvelopeAssinatura e = novo();
        e.marcarRecusado(T1);

        assertThatThrownBy(() -> e.marcarAssinado(T2)).isInstanceOf(ContratoEstadoInvalidoException.class);
    }

    @Test
    void marcarRecusado_transicionaEFinal() {
        EnvelopeAssinatura e = novo();

        e.marcarRecusado(T1);

        assertThat(e.getStatus()).isEqualTo(StatusEnvelope.RECUSADO);
        assertThat(e.getStatus().isFinal()).isTrue();
    }

    @Test
    void marcarRecusado_aposAssinado_rejeita() {
        EnvelopeAssinatura e = novo();
        e.marcarAssinado(T1);

        assertThatThrownBy(() -> e.marcarRecusado(T2)).isInstanceOf(ContratoEstadoInvalidoException.class);
    }

    @Test
    void marcarExpirado_transicionaEFinal() {
        EnvelopeAssinatura e = novo();

        e.marcarExpirado(T1);

        assertThat(e.getStatus()).isEqualTo(StatusEnvelope.EXPIRADO);
        assertThat(e.getStatus().isFinal()).isTrue();
    }

    @Test
    void marcarExpirado_aposAssinado_rejeita() {
        EnvelopeAssinatura e = novo();
        e.marcarAssinado(T1);

        assertThatThrownBy(() -> e.marcarExpirado(T2)).isInstanceOf(ContratoEstadoInvalidoException.class);
    }

    @Test
    void statusEnvelope_isFinalApenasParaTerminais() {
        assertThat(StatusEnvelope.ASSINADO.isFinal()).isTrue();
        assertThat(StatusEnvelope.RECUSADO.isFinal()).isTrue();
        assertThat(StatusEnvelope.EXPIRADO.isFinal()).isTrue();
        assertThat(StatusEnvelope.ENVIADO.isFinal()).isFalse();
        assertThat(StatusEnvelope.VISUALIZADO.isFinal()).isFalse();
        assertThat(StatusEnvelope.RASCUNHO.isFinal()).isFalse();
    }

    private EnvelopeAssinatura novo() {
        return EnvelopeAssinatura.criar(
                UUID.randomUUID(), UUID.randomUUID(), "clicksign", "EXT-1", "IDEMP-1", HASH, T0);
    }
}
