package com.dynamis.sep_api.credores.infrastructure.adapter.escrow;

import com.dynamis.sep_api.credores.application.port.out.AporteEscrowRegistrado;
import com.dynamis.sep_api.credores.application.port.out.RegistrarAporteEscrowCommand;
import com.dynamis.sep_api.credores.domain.exception.AporteEscrowException;
import com.dynamis.sep_api.escrow.application.dto.RegistrarMovimentacaoEscrowCommand;
import com.dynamis.sep_api.escrow.application.usecase.RegistrarMovimentacaoEscrowUseCase;
import com.dynamis.sep_api.escrow.domain.model.ContaEscrow;
import com.dynamis.sep_api.escrow.domain.model.MovimentacaoEscrow;
import com.dynamis.sep_api.escrow.domain.model.Wallet;
import com.dynamis.sep_api.escrow.domain.vo.StatusContaEscrow;
import com.dynamis.sep_api.escrow.domain.vo.TipoWallet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testes do adapter de registro de aporte no escrow (Sprint 29 Task 29.2): traducao do comando,
 * namespacing da chave de idempotencia, referencia interna retornada e sanitizacao de erro bruto.
 */
class AporteEscrowAdapterTest {

    private static final Instant AGORA = Instant.parse("2026-07-09T12:00:00Z");

    private RegistrarMovimentacaoEscrowUseCase escrowUseCase;
    private AporteEscrowAdapter adapter;

    @BeforeEach
    void setup() {
        escrowUseCase = mock(RegistrarMovimentacaoEscrowUseCase.class);
        adapter = new AporteEscrowAdapter(escrowUseCase, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    @Test
    void registroAceitoRetornaReferenciaInternaEStatusProcessavel() {
        UUID aporteId = UUID.randomUUID();
        UUID propostaId = UUID.randomUUID();
        MovimentacaoEscrow movimentacao = movimentacaoDeAporte(propostaId, "aporte:" + aporteId, aporteId);
        when(escrowUseCase.registrarAporte(any())).thenReturn(movimentacao);

        AporteEscrowRegistrado resultado =
                adapter.registrar(new RegistrarAporteEscrowCommand(aporteId, propostaId, new BigDecimal("2500.00")));

        assertThat(resultado.referenciaEscrow()).isEqualTo(movimentacao.getId().toString());
        assertThat(resultado.status()).isEqualTo("EM_PROCESSAMENTO");

        ArgumentCaptor<RegistrarMovimentacaoEscrowCommand> captor =
                ArgumentCaptor.forClass(RegistrarMovimentacaoEscrowCommand.class);
        verify(escrowUseCase).registrarAporte(captor.capture());
        RegistrarMovimentacaoEscrowCommand delegado = captor.getValue();
        assertThat(delegado.idempotencyKey()).isEqualTo("aporte:" + aporteId);
        assertThat(delegado.propostaId()).isEqualTo(propostaId);
        assertThat(delegado.valor()).isEqualByComparingTo("2500.00");
        assertThat(delegado.externalReferenceId()).isEqualTo(aporteId);
        assertThat(delegado.dataMovimentacao()).isEqualTo(OffsetDateTime.ofInstant(AGORA, ZoneOffset.UTC));
    }

    @Test
    void replayComMesmoAporteDelegaMesmaChaveERetornaMesmoResultado() {
        UUID aporteId = UUID.randomUUID();
        UUID propostaId = UUID.randomUUID();
        MovimentacaoEscrow movimentacao = movimentacaoDeAporte(propostaId, "aporte:" + aporteId, aporteId);
        when(escrowUseCase.registrarAporte(any())).thenReturn(movimentacao);
        RegistrarAporteEscrowCommand comando =
                new RegistrarAporteEscrowCommand(aporteId, propostaId, new BigDecimal("100.00"));

        AporteEscrowRegistrado primeiro = adapter.registrar(comando);
        AporteEscrowRegistrado replay = adapter.registrar(comando);

        assertThat(replay).isEqualTo(primeiro);
        ArgumentCaptor<RegistrarMovimentacaoEscrowCommand> captor =
                ArgumentCaptor.forClass(RegistrarMovimentacaoEscrowCommand.class);
        verify(escrowUseCase, org.mockito.Mockito.times(2)).registrarAporte(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(RegistrarMovimentacaoEscrowCommand::idempotencyKey)
                .containsExactly("aporte:" + aporteId, "aporte:" + aporteId);
    }

    @Test
    void falhaBrutaDoEscrowViraExcecaoSanitizadaComCausaPreservada() {
        UUID aporteId = UUID.randomUUID();
        DataIntegrityViolationException erroBruto =
                new DataIntegrityViolationException("constraint uq_wallet_proposta violada wallet=abc-123");
        when(escrowUseCase.registrarAporte(any())).thenThrow(erroBruto);

        Throwable lancada = catchThrowable(() -> adapter.registrar(
                new RegistrarAporteEscrowCommand(aporteId, UUID.randomUUID(), new BigDecimal("50.00"))));

        assertThat(lancada)
                .isInstanceOf(AporteEscrowException.class)
                .hasMessage(AporteEscrowException.MOTIVO_SANITIZADO)
                .hasMessageNotContaining("constraint")
                .hasMessageNotContaining("wallet")
                .hasCause(erroBruto);
    }

    private static MovimentacaoEscrow movimentacaoDeAporte(UUID propostaId, String key, UUID aporteId) {
        ContaEscrow conta = ContaEscrow.criar("SEP-COBRANCA", StatusContaEscrow.ATIVA);
        Wallet wallet = Wallet.criar(conta, propostaId, TipoWallet.PROPOSTA);
        return MovimentacaoEscrow.criarAporte(
                wallet, new BigDecimal("2500.00"), key, OffsetDateTime.ofInstant(AGORA, ZoneOffset.UTC), aporteId);
    }
}
