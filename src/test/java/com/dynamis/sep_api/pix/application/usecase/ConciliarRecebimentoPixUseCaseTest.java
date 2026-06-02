package com.dynamis.sep_api.pix.application.usecase;

import com.dynamis.sep_api.pix.application.port.out.CobrancaRecebimentoPixPort;
import com.dynamis.sep_api.pix.application.port.out.dto.RecebimentoPixCobrancaResult;
import com.dynamis.sep_api.pix.application.port.out.dto.RegistrarRecebimentoPixCobrancaCommand;
import com.dynamis.sep_api.pix.domain.model.PixRecebimento;
import com.dynamis.sep_api.pix.domain.model.PixReferenciaRecebimento;
import com.dynamis.sep_api.pix.domain.vo.StatusPixRecebimento;
import com.dynamis.sep_api.pix.domain.vo.StatusPixReferenciaRecebimento;
import com.dynamis.sep_api.pix.infrastructure.persistence.PixRecebimentoRepository;
import com.dynamis.sep_api.pix.infrastructure.persistence.PixReferenciaRecebimentoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Conciliacao da baixa de parcela a partir de um recebimento Pix (Sprint 21 Task 21.4): exato quita a
 * referencia; parcial/maior ficam DIVERGENTE; sem {@code endToEndId} nao baixa; replay e idempotente.
 */
class ConciliarRecebimentoPixUseCaseTest {

    private static final BigDecimal VALOR_ESPERADO = new BigDecimal("250.00");

    private PixRecebimentoRepository recebimentoRepository;
    private PixReferenciaRecebimentoRepository referenciaRepository;
    private CobrancaRecebimentoPixPort cobrancaPort;
    private ConciliarRecebimentoPixUseCase useCase;

    @BeforeEach
    void setUp() {
        recebimentoRepository = mock(PixRecebimentoRepository.class);
        referenciaRepository = mock(PixReferenciaRecebimentoRepository.class);
        cobrancaPort = mock(CobrancaRecebimentoPixPort.class);
        useCase = new ConciliarRecebimentoPixUseCase(recebimentoRepository, referenciaRepository, cobrancaPort);
    }

    private PixReferenciaRecebimento referenciaAtiva() {
        return PixReferenciaRecebimento.criar(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), VALOR_ESPERADO, "txid-1", "corr-1");
    }

    private PixRecebimento recebimentoEmProcessamento(
            String endToEndId, BigDecimal valor, PixReferenciaRecebimento ref) {
        PixRecebimento r = PixRecebimento.registrar(endToEndId, valor, OffsetDateTime.now(), "corr-1");
        r.vincularReferencia(ref.getId(), ref.getParcelaId());
        return r;
    }

    private void stub(PixRecebimento recebimento, PixReferenciaRecebimento referencia) {
        when(recebimentoRepository.findById(recebimento.getId())).thenReturn(Optional.of(recebimento));
        when(referenciaRepository.findById(referencia.getId())).thenReturn(Optional.of(referencia));
    }

    @Test
    void valorExatoComParcelaQuitada_conciliaEMarcaReferenciaPaga() {
        PixReferenciaRecebimento ref = referenciaAtiva();
        PixRecebimento receb = recebimentoEmProcessamento("E2E-1", VALOR_ESPERADO, ref);
        stub(receb, ref);
        UUID recebimentoCobrancaId = UUID.randomUUID();
        when(cobrancaPort.registrarRecebimento(any()))
                .thenReturn(new RecebimentoPixCobrancaResult(recebimentoCobrancaId, true, true));

        useCase.conciliar(receb.getId());

        assertThat(receb.getStatus()).isEqualTo(StatusPixRecebimento.CONCILIADO);
        assertThat(receb.getRecebimentoCobrancaId()).isEqualTo(recebimentoCobrancaId);
        assertThat(ref.getStatus()).isEqualTo(StatusPixReferenciaRecebimento.PAGA);

        ArgumentCaptor<RegistrarRecebimentoPixCobrancaCommand> cmd =
                ArgumentCaptor.forClass(RegistrarRecebimentoPixCobrancaCommand.class);
        verify(cobrancaPort).registrarRecebimento(cmd.capture());
        assertThat(cmd.getValue().idempotencyKey()).isEqualTo("pix:E2E-1");
        assertThat(cmd.getValue().identificadorExterno()).isEqualTo("E2E-1");
        assertThat(cmd.getValue().parcelaId()).isEqualTo(ref.getParcelaId());
        assertThat(cmd.getValue().registradoPor()).isEqualTo(ref.getTomadorId());
    }

    @Test
    void pagamentoParcial_conciliaMasReferenciaDivergente() {
        PixReferenciaRecebimento ref = referenciaAtiva();
        PixRecebimento receb = recebimentoEmProcessamento("E2E-1", new BigDecimal("100.00"), ref);
        stub(receb, ref);
        when(cobrancaPort.registrarRecebimento(any()))
                .thenReturn(new RecebimentoPixCobrancaResult(UUID.randomUUID(), false, true));

        useCase.conciliar(receb.getId());

        assertThat(receb.getStatus()).isEqualTo(StatusPixRecebimento.CONCILIADO);
        assertThat(ref.getStatus()).isEqualTo(StatusPixReferenciaRecebimento.DIVERGENTE);
    }

    @Test
    void valorMaiorQueEsperado_referenciaDivergente() {
        PixReferenciaRecebimento ref = referenciaAtiva();
        PixRecebimento receb = recebimentoEmProcessamento("E2E-1", new BigDecimal("300.00"), ref);
        stub(receb, ref);
        when(cobrancaPort.registrarRecebimento(any()))
                .thenReturn(new RecebimentoPixCobrancaResult(UUID.randomUUID(), true, true));

        useCase.conciliar(receb.getId());

        assertThat(receb.getStatus()).isEqualTo(StatusPixRecebimento.CONCILIADO);
        assertThat(ref.getStatus()).isEqualTo(StatusPixReferenciaRecebimento.DIVERGENTE);
    }

    @Test
    void endToEndIdAusente_naoBaixaEVaiParaDivergencia() {
        PixReferenciaRecebimento ref = referenciaAtiva();
        PixRecebimento receb = recebimentoEmProcessamento(null, VALOR_ESPERADO, ref);
        stub(receb, ref);

        useCase.conciliar(receb.getId());

        assertThat(receb.getStatus()).isEqualTo(StatusPixRecebimento.NAO_IDENTIFICADO);
        assertThat(ref.getStatus()).isEqualTo(StatusPixReferenciaRecebimento.DIVERGENTE);
        verify(cobrancaPort, never()).registrarRecebimento(any());
    }

    @Test
    void recebimentoForaDeEmProcessamento_idempotenteNaoBaixa() {
        PixReferenciaRecebimento ref = referenciaAtiva();
        PixRecebimento receb = PixRecebimento.registrar("E2E-1", VALOR_ESPERADO, OffsetDateTime.now(), "corr-1");
        when(recebimentoRepository.findById(receb.getId())).thenReturn(Optional.of(receb));

        useCase.conciliar(receb.getId());

        assertThat(receb.getStatus()).isEqualTo(StatusPixRecebimento.RECEBIDO);
        verify(cobrancaPort, never()).registrarRecebimento(any());
    }

    @Test
    void baixaPropagaExcecao_paraOCallerMarcarFalhou() {
        PixReferenciaRecebimento ref = referenciaAtiva();
        PixRecebimento receb = recebimentoEmProcessamento("E2E-1", VALOR_ESPERADO, ref);
        stub(receb, ref);
        when(cobrancaPort.registrarRecebimento(any())).thenThrow(new IllegalStateException("parcela nao recebivel"));

        assertThatThrownBy(() -> useCase.conciliar(receb.getId())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void marcarFalha_marcaRecebimentoFalhou() {
        PixReferenciaRecebimento ref = referenciaAtiva();
        PixRecebimento receb = recebimentoEmProcessamento("E2E-1", VALOR_ESPERADO, ref);
        when(recebimentoRepository.findById(receb.getId())).thenReturn(Optional.of(receb));

        useCase.marcarFalha(receb.getId(), "erro tecnico");

        assertThat(receb.getStatus()).isEqualTo(StatusPixRecebimento.FALHOU);
        assertThat(receb.getMotivoDivergencia()).isEqualTo("erro tecnico");
    }
}
