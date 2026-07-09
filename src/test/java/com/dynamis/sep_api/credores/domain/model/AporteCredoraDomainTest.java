package com.dynamis.sep_api.credores.domain.model;

import com.dynamis.sep_api.credores.domain.vo.StatusAporteCredora;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Testes de dominio das transicoes do aporte da credora (Sprint 29 Task 29.1).
 *
 * <p>Decisao registrada: o aporte nasce em {@link StatusAporteCredora#PENDENTE} apos o registro
 * local, ANTES de qualquer chamada ao escrow — padrao anti-orphan ja usado por {@code
 * PixTransferencia.criar} (Sprint 20). {@code FALHOU} e permitido a partir de {@code PENDENTE}
 * (registro recusado pelo provider) e de {@code EM_PROCESSAMENTO} (falha na liquidacao).
 */
class AporteCredoraDomainTest {

    private static final String IDEMPOTENCY_KEY = "idem-key-aporte-1";
    private static final String REFERENCIA_ESCROW = "mov-escrow-interna-1";

    private static AporteCredora novoAporte() {
        return AporteCredora.registrar(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("1500.5"), IDEMPOTENCY_KEY);
    }

    @Test
    void registrarNascePendenteComValorNormalizadoEMoedaBrl() {
        AporteCredora aporte = novoAporte();

        assertThat(aporte.getId()).isNotNull();
        assertThat(aporte.getStatus()).isEqualTo(StatusAporteCredora.PENDENTE);
        assertThat(aporte.getValor()).isEqualByComparingTo("1500.50");
        assertThat(aporte.getValor().scale()).isEqualTo(2);
        assertThat(aporte.getMoeda()).isEqualTo("BRL");
        assertThat(aporte.getIdempotencyKey()).isEqualTo(IDEMPOTENCY_KEY);
        assertThat(aporte.getReferenciaEscrow()).isNull();
        assertThat(aporte.getMotivoFalhaSanitizado()).isNull();
    }

    @Test
    void registrarValidaObrigatoriosValorEIdempotencyKey() {
        UUID operacaoId = UUID.randomUUID();
        UUID credoraId = UUID.randomUUID();

        assertThatNullPointerException()
                .isThrownBy(() -> AporteCredora.registrar(null, credoraId, BigDecimal.TEN, "k"));
        assertThatNullPointerException()
                .isThrownBy(() -> AporteCredora.registrar(operacaoId, null, BigDecimal.TEN, "k"));
        assertThatNullPointerException().isThrownBy(() -> AporteCredora.registrar(operacaoId, credoraId, null, "k"));
        assertThatNullPointerException()
                .isThrownBy(() -> AporteCredora.registrar(operacaoId, credoraId, BigDecimal.TEN, null));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> AporteCredora.registrar(operacaoId, credoraId, BigDecimal.ZERO, "k"));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> AporteCredora.registrar(operacaoId, credoraId, new BigDecimal("-10.00"), "k"));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> AporteCredora.registrar(operacaoId, credoraId, new BigDecimal("10.123"), "k"));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> AporteCredora.registrar(operacaoId, credoraId, BigDecimal.TEN, "  "));
    }

    @Test
    void aceiteDoProviderAvancaParaEmProcessamentoComReferenciaEscrow() {
        AporteCredora aporte = novoAporte();

        aporte.marcarEmProcessamento(REFERENCIA_ESCROW);

        assertThat(aporte.getStatus()).isEqualTo(StatusAporteCredora.EM_PROCESSAMENTO);
        assertThat(aporte.getReferenciaEscrow()).isEqualTo(REFERENCIA_ESCROW);
    }

    @Test
    void marcarEmProcessamentoExigeReferenciaEscrow() {
        AporteCredora aporte = novoAporte();

        assertThatIllegalArgumentException().isThrownBy(() -> aporte.marcarEmProcessamento(" "));
        assertThat(aporte.getStatus()).isEqualTo(StatusAporteCredora.PENDENTE);
    }

    @Test
    void liquidaAPartirDeEmProcessamento() {
        AporteCredora aporte = novoAporte();
        aporte.marcarEmProcessamento(REFERENCIA_ESCROW);

        aporte.marcarLiquidado();

        assertThat(aporte.getStatus()).isEqualTo(StatusAporteCredora.LIQUIDADO);
    }

    @Test
    void falhaAPartirDeEmProcessamentoComMotivoSanitizado() {
        AporteCredora aporte = novoAporte();
        aporte.marcarEmProcessamento(REFERENCIA_ESCROW);

        aporte.marcarFalhou("Falha na liquidacao do aporte");

        assertThat(aporte.getStatus()).isEqualTo(StatusAporteCredora.FALHOU);
        assertThat(aporte.getMotivoFalhaSanitizado()).isEqualTo("Falha na liquidacao do aporte");
    }

    @Test
    void falhaAindaPendenteQuandoRegistroRecusadoPeloProvider() {
        AporteCredora aporte = novoAporte();

        aporte.marcarFalhou("Registro do aporte recusado");

        assertThat(aporte.getStatus()).isEqualTo(StatusAporteCredora.FALHOU);
        assertThat(aporte.getReferenciaEscrow()).isNull();
    }

    @Test
    void marcarFalhouExigeMotivoSanitizado() {
        AporteCredora aporte = novoAporte();

        assertThatIllegalArgumentException().isThrownBy(() -> aporte.marcarFalhou(" "));
        assertThat(aporte.getStatus()).isEqualTo(StatusAporteCredora.PENDENTE);
    }

    @Test
    void transicoesInvalidasFalhamSemAlterarEstado() {
        AporteCredora pendente = novoAporte();
        assertThatIllegalStateException().isThrownBy(pendente::marcarLiquidado);
        assertThat(pendente.getStatus()).isEqualTo(StatusAporteCredora.PENDENTE);

        AporteCredora liquidado = novoAporte();
        liquidado.marcarEmProcessamento(REFERENCIA_ESCROW);
        liquidado.marcarLiquidado();
        assertThatIllegalStateException().isThrownBy(() -> liquidado.marcarEmProcessamento("outra-ref"));
        assertThatIllegalStateException().isThrownBy(() -> liquidado.marcarFalhou("motivo"));
        assertThatIllegalStateException().isThrownBy(liquidado::marcarLiquidado);
        assertThat(liquidado.getStatus()).isEqualTo(StatusAporteCredora.LIQUIDADO);
        assertThat(liquidado.getReferenciaEscrow()).isEqualTo(REFERENCIA_ESCROW);

        AporteCredora falhou = novoAporte();
        falhou.marcarFalhou("Registro do aporte recusado");
        assertThatIllegalStateException().isThrownBy(() -> falhou.marcarEmProcessamento(REFERENCIA_ESCROW));
        assertThatIllegalStateException().isThrownBy(falhou::marcarLiquidado);
        assertThat(falhou.getStatus()).isEqualTo(StatusAporteCredora.FALHOU);
    }

    @Test
    void mensagensDeErroEToStringNaoExpoemDadosSensiveis() {
        AporteCredora aporte = novoAporte();
        aporte.marcarEmProcessamento(REFERENCIA_ESCROW);

        Throwable transicaoInvalida = catchThrowable(() -> aporte.marcarEmProcessamento("outra-ref"));

        assertThat(transicaoInvalida)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining(REFERENCIA_ESCROW)
                .hasMessageNotContaining(IDEMPOTENCY_KEY)
                .hasMessageNotContaining(aporte.getId().toString())
                .hasMessageNotContaining(aporte.getOperacaoId().toString());
        assertThat(aporte.toString()).doesNotContain(REFERENCIA_ESCROW).doesNotContain(IDEMPOTENCY_KEY);
    }
}
