package com.dynamis.sep_api.pix.application.mapper;

import com.dynamis.sep_api.pix.application.dto.PixPagamentoParcelaResult;
import com.dynamis.sep_api.pix.domain.model.PixRecebimento;
import com.dynamis.sep_api.pix.domain.model.PixReferenciaRecebimento;
import com.dynamis.sep_api.pix.domain.vo.StatusPixParcelaPublico;
import com.dynamis.sep_api.pix.domain.vo.StatusPixRecebimento;
import com.dynamis.sep_api.pix.domain.vo.StatusPixReferenciaRecebimento;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StatusPixParcelaPublicoMapperTest {

    private static final OffsetDateTime DATA_REF = OffsetDateTime.parse("2026-06-01T10:00:00Z");
    private static final OffsetDateTime DATA_REC = OffsetDateTime.parse("2026-06-02T10:00:00Z");
    private static final BigDecimal VALOR = new BigDecimal("350.00");

    @Test
    void ativaSemRecebimento_viraAguardando() {
        PixPagamentoParcelaResult r =
                StatusPixParcelaPublicoMapper.mapear(ref(StatusPixReferenciaRecebimento.ATIVA), null);
        assertThat(r.status()).isEqualTo(StatusPixParcelaPublico.AGUARDANDO);
        assertThat(r.valor()).isEqualByComparingTo(VALOR);
        assertThat(r.atualizadoEm()).isEqualTo(DATA_REF);
        assertThat(r.mensagemPublica()).isNull();
    }

    @Test
    void recebidoOuEmProcessamento_viraEmProcessamento_comDataDoRecebimento() {
        PixPagamentoParcelaResult recebido = StatusPixParcelaPublicoMapper.mapear(
                ref(StatusPixReferenciaRecebimento.ATIVA), rec(StatusPixRecebimento.RECEBIDO));
        assertThat(recebido.status()).isEqualTo(StatusPixParcelaPublico.EM_PROCESSAMENTO);
        assertThat(recebido.atualizadoEm()).isEqualTo(DATA_REC);

        PixPagamentoParcelaResult processando = StatusPixParcelaPublicoMapper.mapear(
                ref(StatusPixReferenciaRecebimento.ATIVA), rec(StatusPixRecebimento.EM_PROCESSAMENTO));
        assertThat(processando.status()).isEqualTo(StatusPixParcelaPublico.EM_PROCESSAMENTO);
    }

    @Test
    void referenciaPagaOuRecebimentoConciliado_viraLiquidado() {
        assertThat(StatusPixParcelaPublicoMapper.mapear(ref(StatusPixReferenciaRecebimento.PAGA), null)
                        .status())
                .isEqualTo(StatusPixParcelaPublico.LIQUIDADO);

        PixPagamentoParcelaResult conciliado = StatusPixParcelaPublicoMapper.mapear(
                ref(StatusPixReferenciaRecebimento.ATIVA), rec(StatusPixRecebimento.CONCILIADO));
        assertThat(conciliado.status()).isEqualTo(StatusPixParcelaPublico.LIQUIDADO);
        assertThat(conciliado.atualizadoEm()).isEqualTo(DATA_REC);
    }

    @Test
    void expiradaViraExpirado() {
        assertThat(StatusPixParcelaPublicoMapper.mapear(ref(StatusPixReferenciaRecebimento.EXPIRADA), null)
                        .status())
                .isEqualTo(StatusPixParcelaPublico.EXPIRADO);
    }

    @Test
    void canceladaViraCancelado() {
        assertThat(StatusPixParcelaPublicoMapper.mapear(ref(StatusPixReferenciaRecebimento.CANCELADA), null)
                        .status())
                .isEqualTo(StatusPixParcelaPublico.CANCELADO);
    }

    @Test
    void referenciaDivergenteOuRecebimentoNaoIdentificado_viraDivergente_comMensagem() {
        PixPagamentoParcelaResult porReferencia =
                StatusPixParcelaPublicoMapper.mapear(ref(StatusPixReferenciaRecebimento.DIVERGENTE), null);
        assertThat(porReferencia.status()).isEqualTo(StatusPixParcelaPublico.DIVERGENTE);
        assertThat(porReferencia.mensagemPublica()).isNotBlank();
        assertThat(porReferencia.atualizadoEm()).isEqualTo(DATA_REF);

        PixPagamentoParcelaResult porRecebimento = StatusPixParcelaPublicoMapper.mapear(
                ref(StatusPixReferenciaRecebimento.ATIVA), rec(StatusPixRecebimento.NAO_IDENTIFICADO));
        assertThat(porRecebimento.status()).isEqualTo(StatusPixParcelaPublico.DIVERGENTE);
        assertThat(porRecebimento.atualizadoEm()).isEqualTo(DATA_REC);
    }

    @Test
    void recebimentoFalhou_viraFalhou_comMensagem() {
        PixPagamentoParcelaResult r = StatusPixParcelaPublicoMapper.mapear(
                ref(StatusPixReferenciaRecebimento.ATIVA), rec(StatusPixRecebimento.FALHOU));
        assertThat(r.status()).isEqualTo(StatusPixParcelaPublico.FALHOU);
        assertThat(r.mensagemPublica()).isNotBlank();
        assertThat(r.atualizadoEm()).isEqualTo(DATA_REC);
    }

    @Test
    void mensagemPublicaSomenteEmEstadosDeAtencao() {
        assertThat(StatusPixParcelaPublicoMapper.mapear(ref(StatusPixReferenciaRecebimento.ATIVA), null)
                        .mensagemPublica())
                .isNull();
        assertThat(StatusPixParcelaPublicoMapper.mapear(ref(StatusPixReferenciaRecebimento.PAGA), null)
                        .mensagemPublica())
                .isNull();
    }

    private static PixReferenciaRecebimento ref(StatusPixReferenciaRecebimento status) {
        PixReferenciaRecebimento referencia = mock(PixReferenciaRecebimento.class);
        when(referencia.getStatus()).thenReturn(status);
        when(referencia.getValorEsperado()).thenReturn(VALOR);
        when(referencia.getDataModificacao()).thenReturn(DATA_REF);
        return referencia;
    }

    private static PixRecebimento rec(StatusPixRecebimento status) {
        PixRecebimento recebimento = mock(PixRecebimento.class);
        when(recebimento.getStatus()).thenReturn(status);
        when(recebimento.getDataModificacao()).thenReturn(DATA_REC);
        return recebimento;
    }
}
