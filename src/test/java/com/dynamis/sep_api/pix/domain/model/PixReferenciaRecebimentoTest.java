package com.dynamis.sep_api.pix.domain.model;

import com.dynamis.sep_api.pix.domain.vo.StatusPixReferenciaRecebimento;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PixReferenciaRecebimentoTest {

    private static final UUID PARCELA = UUID.randomUUID();
    private static final UUID CONTRATO = UUID.randomUUID();
    private static final UUID TOMADOR = UUID.randomUUID();
    private static final BigDecimal VALOR = new BigDecimal("500.00");

    private PixReferenciaRecebimento criar() {
        return PixReferenciaRecebimento.criar(PARCELA, CONTRATO, TOMADOR, VALOR, "txid-1", "corr-1");
    }

    @Test
    void criar_nasceAtivaComCampos() {
        PixReferenciaRecebimento r = criar();

        assertThat(r.getStatus()).isEqualTo(StatusPixReferenciaRecebimento.ATIVA);
        assertThat(r.getParcelaId()).isEqualTo(PARCELA);
        assertThat(r.getTxid()).isEqualTo("txid-1");
        assertThat(r.getValorEsperado()).isEqualByComparingTo(VALOR);
        assertThat(r.getId()).isNotNull();
    }

    @Test
    void marcarPaga_deAtiva_terminal() {
        PixReferenciaRecebimento r = criar();
        r.marcarPaga();
        assertThat(r.getStatus()).isEqualTo(StatusPixReferenciaRecebimento.PAGA);
    }

    @Test
    void marcarPaga_naoVoltaParaAtiva() {
        PixReferenciaRecebimento r = criar();
        r.marcarPaga();
        // qualquer transicao a partir de PAGA falha (terminal).
        assertThatThrownBy(r::marcarDivergente).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(r::cancelar).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void transicoesDeAtiva() {
        assertThat(criarEAplicar(PixReferenciaRecebimento::marcarDivergente))
                .isEqualTo(StatusPixReferenciaRecebimento.DIVERGENTE);
        assertThat(criarEAplicar(PixReferenciaRecebimento::marcarExpirada))
                .isEqualTo(StatusPixReferenciaRecebimento.EXPIRADA);
        assertThat(criarEAplicar(PixReferenciaRecebimento::cancelar))
                .isEqualTo(StatusPixReferenciaRecebimento.CANCELADA);
    }

    private StatusPixReferenciaRecebimento criarEAplicar(java.util.function.Consumer<PixReferenciaRecebimento> op) {
        PixReferenciaRecebimento r = criar();
        op.accept(r);
        return r.getStatus();
    }

    @Test
    void valorNaoPositivo_rejeita() {
        assertThatThrownBy(() ->
                        PixReferenciaRecebimento.criar(PARCELA, CONTRATO, TOMADOR, BigDecimal.ZERO, "txid-1", "corr-1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void txidVazio_rejeita() {
        assertThatThrownBy(() -> PixReferenciaRecebimento.criar(PARCELA, CONTRATO, TOMADOR, VALOR, "  ", "corr-1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void vincularProvider_guardaIdECopiaCola() {
        PixReferenciaRecebimento r = criar();
        r.vincularProvider("prov-1", "0002012636...");
        assertThat(r.getProviderReferenciaId()).isEqualTo("prov-1");
        assertThat(r.getCodigoCopiaCola()).startsWith("0002");
    }
}
