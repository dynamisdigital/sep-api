package com.dynamis.sep_api.pix.domain.model;

import com.dynamis.sep_api.pix.domain.vo.StatusPixTransferencia;
import com.dynamis.sep_api.pix.domain.vo.TipoPixTransferencia;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PixTransferenciaDesembolsoTest {

    private static final UUID CONTRATO = UUID.randomUUID();
    private static final UUID PROPOSTA = UUID.randomUUID();
    private static final UUID TOMADOR = UUID.randomUUID();
    private static final BigDecimal VALOR = new BigDecimal("10000.00");

    private PixTransferencia criar() {
        return PixTransferencia.criarDesembolso(
                CONTRATO, PROPOSTA, TOMADOR, VALOR, "a".repeat(64), "us****om", "idem-1", "corr-1");
    }

    @Test
    void criarDesembolso_nasceCriadaComVinculos() {
        PixTransferencia t = criar();

        assertThat(t.getStatus()).isEqualTo(StatusPixTransferencia.CRIADA);
        assertThat(t.getTipoTransferencia()).isEqualTo(TipoPixTransferencia.DESEMBOLSO_CONTRATO);
        assertThat(t.getContratoId()).isEqualTo(CONTRATO);
        assertThat(t.getPropostaId()).isEqualTo(PROPOSTA);
        assertThat(t.getTomadorId()).isEqualTo(TOMADOR);
        assertThat(t.getValor()).isEqualByComparingTo(VALOR);
        assertThat(t.getChaveDestinoHash()).hasSize(64);
        assertThat(t.getChaveDestinoMascara()).isEqualTo("us****om");
        assertThat(t.getId()).isNotNull();
    }

    @Test
    void criarDesembolso_valorNaoPositivo_rejeita() {
        assertThatThrownBy(() -> PixTransferencia.criarDesembolso(
                        CONTRATO, PROPOSTA, TOMADOR, BigDecimal.ZERO, "h", "m", "idem-1", "corr-1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void criarDesembolso_contratoNulo_rejeita() {
        assertThatThrownBy(() ->
                        PixTransferencia.criarDesembolso(null, PROPOSTA, TOMADOR, VALOR, "h", "m", "idem-1", "corr-1"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void criarDesembolso_idempotencyKeyVazia_rejeita() {
        assertThatThrownBy(() ->
                        PixTransferencia.criarDesembolso(CONTRATO, PROPOSTA, TOMADOR, VALOR, "h", "m", "  ", "corr-1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void desembolso_segueTransicoesDeStatus() {
        PixTransferencia t = criar();
        t.marcarSolicitada("ext-1");
        assertThat(t.getStatus()).isEqualTo(StatusPixTransferencia.SOLICITADA);
        assertThat(t.getExternalId()).isEqualTo("ext-1");
        t.marcarConcluida();
        assertThat(t.getStatus()).isEqualTo(StatusPixTransferencia.CONCLUIDA);
    }
}
