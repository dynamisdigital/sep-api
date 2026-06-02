package com.dynamis.sep_api.pix.application.usecase;

import com.dynamis.sep_api.pix.application.dto.GerarReferenciaRecebimentoPixCommand;
import com.dynamis.sep_api.pix.application.dto.GerarReferenciaRecebimentoPixResult;
import com.dynamis.sep_api.pix.application.port.out.CobrancaRecebimentoPixQueryPort;
import com.dynamis.sep_api.pix.application.port.out.PixProvider;
import com.dynamis.sep_api.pix.application.port.out.dto.ParcelaRecebimentoPixView;
import com.dynamis.sep_api.pix.application.port.out.dto.RespostaCobrancaPix;
import com.dynamis.sep_api.pix.application.port.out.exception.PixProviderException;
import com.dynamis.sep_api.pix.domain.model.PixReferenciaRecebimento;
import com.dynamis.sep_api.pix.domain.vo.StatusPixReferenciaRecebimento;
import com.dynamis.sep_api.pix.infrastructure.persistence.PixReferenciaRecebimentoRepository;
import com.dynamis.sep_api.shared.exception.ConflitoException;
import com.dynamis.sep_api.shared.exception.OperacaoNaoProcessavelException;
import com.dynamis.sep_api.shared.exception.RecursoNaoEncontradoException;
import com.dynamis.sep_api.shared.exception.ValidacaoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GerarReferenciaRecebimentoPixUseCaseTest {

    private CobrancaRecebimentoPixQueryPort cobrancaQueryPort;
    private PixReferenciaRecebimentoRepository referenciaRepository;
    private PixProvider pixProvider;
    private GerarReferenciaRecebimentoPixUseCase useCase;

    private final UUID parcelaId = UUID.randomUUID();
    private final UUID contratoId = UUID.randomUUID();
    private final UUID tomadorId = UUID.randomUUID();
    private static final BigDecimal VALOR = new BigDecimal("1500.00");

    @BeforeEach
    void setUp() {
        cobrancaQueryPort = mock(CobrancaRecebimentoPixQueryPort.class);
        referenciaRepository = mock(PixReferenciaRecebimentoRepository.class);
        pixProvider = mock(PixProvider.class);
        useCase = new GerarReferenciaRecebimentoPixUseCase(cobrancaQueryPort, referenciaRepository, pixProvider);
    }

    private GerarReferenciaRecebimentoPixCommand comando() {
        return new GerarReferenciaRecebimentoPixCommand(parcelaId, "corr-1");
    }

    private void stubParcela(boolean permiteRecebimento, BigDecimal valorEmAberto) {
        when(cobrancaQueryPort.buscarParcelaParaReferenciaPix(parcelaId))
                .thenReturn(Optional.of(new ParcelaRecebimentoPixView(
                        parcelaId, contratoId, tomadorId, valorEmAberto, permiteRecebimento)));
    }

    private void stubSemReferenciaAtiva() {
        when(referenciaRepository.findByParcelaIdAndStatus(parcelaId, StatusPixReferenciaRecebimento.ATIVA))
                .thenReturn(Optional.empty());
    }

    @Test
    void parcelaElegivel_geraReferenciaAtivaComProvider() {
        stubParcela(true, VALOR);
        stubSemReferenciaAtiva();
        when(referenciaRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(referenciaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(pixProvider.criarCobrancaRecebimento(any(), eq("corr-1"))).thenAnswer(inv -> {
            String txid = ((com.dynamis.sep_api.pix.application.port.out.dto.ComandoCriarCobrancaPix)
                            inv.getArgument(0))
                    .txid();
            return new RespostaCobrancaPix(txid, "prov-ref-1", "copia-cola-" + txid);
        });

        GerarReferenciaRecebimentoPixResult res = useCase.executar(comando());

        assertThat(res.novo()).isTrue();
        assertThat(res.parcelaId()).isEqualTo(parcelaId);
        assertThat(res.status()).isEqualTo(StatusPixReferenciaRecebimento.ATIVA);
        assertThat(res.valorEsperado()).isEqualByComparingTo(VALOR);
        assertThat(res.txid()).hasSize(32);
        assertThat(res.codigoCopiaCola()).isEqualTo("copia-cola-" + res.txid());
    }

    @Test
    void referenciaAtivaExistente_retornaIdempotenteSemProvider() {
        stubParcela(true, VALOR);
        PixReferenciaRecebimento existente = PixReferenciaRecebimento.criar(
                parcelaId, contratoId, tomadorId, VALOR, "txid-existente-aaaaaaaaaaaaaaaa", "corr-0");
        existente.vincularProvider("prov-0", "copia-cola-0");
        when(referenciaRepository.findByParcelaIdAndStatus(parcelaId, StatusPixReferenciaRecebimento.ATIVA))
                .thenReturn(Optional.of(existente));

        GerarReferenciaRecebimentoPixResult res = useCase.executar(comando());

        assertThat(res.novo()).isFalse();
        assertThat(res.referenciaId()).isEqualTo(existente.getId());
        assertThat(res.txid()).isEqualTo("txid-existente-aaaaaaaaaaaaaaaa");
        verify(pixProvider, never()).criarCobrancaRecebimento(any(), any());
        verify(referenciaRepository, never()).saveAndFlush(any());
    }

    @Test
    void parcelaInexistente_naoEncontrado() {
        when(cobrancaQueryPort.buscarParcelaParaReferenciaPix(parcelaId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(comando()))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .extracting("codigo")
                .isEqualTo("PIX-404-PARCELA");
    }

    @Test
    void parcelaNaoRecebivel_naoProcessavel() {
        stubParcela(false, VALOR);

        assertThatThrownBy(() -> useCase.executar(comando()))
                .isInstanceOf(OperacaoNaoProcessavelException.class)
                .extracting("codigo")
                .isEqualTo("PIX-422-PARCELA-NAO-RECEBIVEL");
        verify(pixProvider, never()).criarCobrancaRecebimento(any(), any());
    }

    @Test
    void parcelaSemSaldo_naoProcessavel() {
        stubParcela(true, new BigDecimal("0.00"));

        assertThatThrownBy(() -> useCase.executar(comando()))
                .isInstanceOf(OperacaoNaoProcessavelException.class)
                .extracting("codigo")
                .isEqualTo("PIX-422-PARCELA-SEM-SALDO");
    }

    @Test
    void corridaConcorrente_devolveConflito() {
        stubParcela(true, VALOR);
        stubSemReferenciaAtiva();
        when(referenciaRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("unique"));

        assertThatThrownBy(() -> useCase.executar(comando()))
                .isInstanceOf(ConflitoException.class)
                .extracting("codigo")
                .isEqualTo("PIX-409-REFERENCIA-CONCORRENTE");
        // Provider nao eh chamado: a corrida bate na UNIQUE antes da chamada externa (anti-orphan).
        verify(pixProvider, never()).criarCobrancaRecebimento(any(), any());
    }

    @Test
    void provedorFalha_propagaParaRollback() {
        stubParcela(true, VALOR);
        stubSemReferenciaAtiva();
        when(referenciaRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(pixProvider.criarCobrancaRecebimento(any(), any())).thenThrow(new PixProviderException("timeout"));

        assertThatThrownBy(() -> useCase.executar(comando())).isInstanceOf(PixProviderException.class);
        verify(referenciaRepository, never()).save(any());
    }

    @Test
    void parcelaIdNulo_validacao400() {
        assertThatThrownBy(() -> useCase.executar(new GerarReferenciaRecebimentoPixCommand(null, "corr-1")))
                .isInstanceOf(ValidacaoException.class)
                .extracting("codigo")
                .isEqualTo("PIX-400-PARCELA");
    }
}
