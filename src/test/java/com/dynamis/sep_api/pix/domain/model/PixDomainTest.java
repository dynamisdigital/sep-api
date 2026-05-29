package com.dynamis.sep_api.pix.domain.model;

import com.dynamis.sep_api.pix.domain.vo.StatusPixRecebimento;
import com.dynamis.sep_api.pix.domain.vo.StatusPixTransferencia;
import com.dynamis.sep_api.pix.domain.vo.StatusPixWebhookEvent;
import com.dynamis.sep_api.pix.domain.vo.TipoPixWebhookEvent;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cobre estados validos e invalidos do dominio Pix (Sprint 19 Task 19.1). Testes F.I.R.S.T.: sem
 * I/O, deterministicos e auto-validados.
 */
class PixDomainTest {

    private static final BigDecimal VALOR = new BigDecimal("150.00");
    private static final String HASH64 = "0".repeat(64);

    @Nested
    class Transferencia {

        @Test
        void criarNasceEmCriada() {
            PixTransferencia t = PixTransferencia.criar(VALOR, "pagamento", "idem-1", "corr-1");
            assertThat(t.getStatus()).isEqualTo(StatusPixTransferencia.CRIADA);
            assertThat(t.getId()).isNotNull();
            assertThat(t.getValor()).isEqualByComparingTo(VALOR);
            assertThat(t.getExternalId()).isNull();
        }

        @Test
        void criarRejeitaValorNaoPositivo() {
            assertThatThrownBy(() -> PixTransferencia.criar(BigDecimal.ZERO, "x", "idem-1", "corr-1"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("positivo");
        }

        @Test
        void criarRejeitaIdempotencyKeyBlank() {
            assertThatThrownBy(() -> PixTransferencia.criar(VALOR, "x", "  ", "corr-1"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("idempotencyKey");
        }

        @Test
        void criarRejeitaValorComMaisDeDuasCasas() {
            assertThatThrownBy(() -> PixTransferencia.criar(new BigDecimal("10.005"), "x", "idem-1", "corr-1"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("2 casas");
        }

        @Test
        void fluxoFelizAteConcluida() {
            PixTransferencia t = PixTransferencia.criar(VALOR, "x", "idem-1", "corr-1");
            t.marcarSolicitada("ext-99");
            assertThat(t.getStatus()).isEqualTo(StatusPixTransferencia.SOLICITADA);
            assertThat(t.getExternalId()).isEqualTo("ext-99");
            t.marcarProcessando();
            assertThat(t.getStatus()).isEqualTo(StatusPixTransferencia.PROCESSANDO);
            t.marcarConcluida();
            assertThat(t.getStatus()).isEqualTo(StatusPixTransferencia.CONCLUIDA);
        }

        @Test
        void marcarSolicitadaExigeExternalId() {
            PixTransferencia t = PixTransferencia.criar(VALOR, "x", "idem-1", "corr-1");
            assertThatThrownBy(() -> t.marcarSolicitada("  ")).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void naoProcessaSemSolicitar() {
            PixTransferencia t = PixTransferencia.criar(VALOR, "x", "idem-1", "corr-1");
            assertThatThrownBy(t::marcarProcessando)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("CRIADA");
        }

        @Test
        void naoConcluiAposCancelar() {
            PixTransferencia t = PixTransferencia.criar(VALOR, "x", "idem-1", "corr-1");
            t.cancelar();
            assertThat(t.getStatus()).isEqualTo(StatusPixTransferencia.CANCELADA);
            assertThatThrownBy(t::marcarConcluida).isInstanceOf(IllegalStateException.class);
        }

        @Test
        void naoCancelaAposSolicitar() {
            PixTransferencia t = PixTransferencia.criar(VALOR, "x", "idem-1", "corr-1");
            t.marcarSolicitada("ext-99");
            assertThatThrownBy(t::cancelar).isInstanceOf(IllegalStateException.class);
        }

        @Test
        void falhaPermitidaEnquantoAtiva() {
            PixTransferencia t = PixTransferencia.criar(VALOR, "x", "idem-1", "corr-1");
            t.marcarSolicitada("ext-99");
            t.marcarFalhou();
            assertThat(t.getStatus()).isEqualTo(StatusPixTransferencia.FALHOU);
        }
    }

    @Nested
    class Recebimento {

        @Test
        void registrarNasceEmRecebido() {
            PixRecebimento r = PixRecebimento.registrar("E2E-1", VALOR, OffsetDateTime.now(), "corr-1");
            assertThat(r.getStatus()).isEqualTo(StatusPixRecebimento.RECEBIDO);
            assertThat(r.getEndToEndId()).isEqualTo("E2E-1");
        }

        @Test
        void registrarRejeitaValorNaoPositivo() {
            assertThatThrownBy(() -> PixRecebimento.registrar("E2E-1", new BigDecimal("-1.00"), OffsetDateTime.now(), "corr-1"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("positivo");
        }

        @Test
        void permiteEndToEndIdNulo() {
            PixRecebimento r = PixRecebimento.registrar(null, VALOR, OffsetDateTime.now(), "corr-1");
            assertThat(r.getEndToEndId()).isNull();
            assertThat(r.getStatus()).isEqualTo(StatusPixRecebimento.RECEBIDO);
        }

        @Test
        void normalizaEndToEndIdBlankParaNulo() {
            PixRecebimento r = PixRecebimento.registrar("   ", VALOR, OffsetDateTime.now(), "corr-1");
            assertThat(r.getEndToEndId()).isNull();
        }

        @Test
        void registrarRejeitaValorComMaisDeDuasCasas() {
            assertThatThrownBy(() -> PixRecebimento.registrar("E2E-1", new BigDecimal("10.005"), OffsetDateTime.now(), "corr-1"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("2 casas");
        }

        @Test
        void conciliaAposProcessamento() {
            PixRecebimento r = PixRecebimento.registrar("E2E-1", VALOR, OffsetDateTime.now(), "corr-1");
            r.marcarEmProcessamento();
            r.marcarConciliado();
            assertThat(r.getStatus()).isEqualTo(StatusPixRecebimento.CONCILIADO);
        }

        @Test
        void naoReprocessaAposConciliar() {
            PixRecebimento r = PixRecebimento.registrar("E2E-1", VALOR, OffsetDateTime.now(), "corr-1");
            r.marcarConciliado();
            assertThatThrownBy(r::marcarEmProcessamento).isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    class WebhookEvent {

        @Test
        void receberNasceEmRecebido() {
            PixWebhookEvent e = PixWebhookEvent.receber("celcoin", "evt-1", TipoPixWebhookEvent.RECEBIMENTO_PIX, HASH64);
            assertThat(e.getStatus()).isEqualTo(StatusPixWebhookEvent.RECEBIDO);
            assertThat(e.getReceivedAt()).isNotNull();
        }

        @Test
        void receberRejeitaEventIdBlank() {
            assertThatThrownBy(() -> PixWebhookEvent.receber("celcoin", "  ", TipoPixWebhookEvent.DESCONHECIDO, HASH64))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("eventId");
        }

        @Test
        void receberRejeitaPayloadHashNaoSha256() {
            assertThatThrownBy(() -> PixWebhookEvent.receber("celcoin", "evt-1", TipoPixWebhookEvent.RECEBIMENTO_PIX, "hash-abc"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("SHA-256");
        }

        @Test
        void processadoEhIdempotente() {
            PixWebhookEvent e = PixWebhookEvent.receber("celcoin", "evt-1", TipoPixWebhookEvent.RECEBIMENTO_PIX, HASH64);
            e.marcarProcessado();
            e.marcarProcessado();
            assertThat(e.getStatus()).isEqualTo(StatusPixWebhookEvent.PROCESSADO);
            assertThat(e.getErro()).isNull();
        }

        @Test
        void ignoradoRegistraMotivo() {
            PixWebhookEvent e = PixWebhookEvent.receber("celcoin", "evt-1", TipoPixWebhookEvent.DESCONHECIDO, HASH64);
            e.marcarIgnorado("tipo nao mapeado");
            assertThat(e.getStatus()).isEqualTo(StatusPixWebhookEvent.IGNORADO);
            assertThat(e.getErro()).isEqualTo("tipo nao mapeado");
        }

        @Test
        void falhouRegistraErro() {
            PixWebhookEvent e = PixWebhookEvent.receber("celcoin", "evt-1", TipoPixWebhookEvent.RECEBIMENTO_PIX, HASH64);
            e.marcarFalhou("erro tecnico");
            assertThat(e.getStatus()).isEqualTo(StatusPixWebhookEvent.FALHOU);
            assertThat(e.getErro()).isEqualTo("erro tecnico");
        }

        @Test
        void naoReprocessaEventoProcessado() {
            PixWebhookEvent e = PixWebhookEvent.receber("celcoin", "evt-1", TipoPixWebhookEvent.RECEBIMENTO_PIX, HASH64);
            e.marcarProcessado();
            assertThatThrownBy(() -> e.marcarFalhou("x")).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> e.marcarIgnorado("x")).isInstanceOf(IllegalStateException.class);
        }

        @Test
        void eventoFalhadoNaoViraProcessado() {
            PixWebhookEvent e = PixWebhookEvent.receber("celcoin", "evt-1", TipoPixWebhookEvent.RECEBIMENTO_PIX, HASH64);
            e.marcarFalhou("erro");
            assertThatThrownBy(e::marcarProcessado).isInstanceOf(IllegalStateException.class);
        }

        @Test
        void eventoIgnoradoNaoViraProcessado() {
            PixWebhookEvent e = PixWebhookEvent.receber("celcoin", "evt-1", TipoPixWebhookEvent.DESCONHECIDO, HASH64);
            e.marcarIgnorado("desconhecido");
            assertThatThrownBy(e::marcarProcessado).isInstanceOf(IllegalStateException.class);
        }
    }
}
