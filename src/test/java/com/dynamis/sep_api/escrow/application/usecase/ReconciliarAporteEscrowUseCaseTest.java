package com.dynamis.sep_api.escrow.application.usecase;

import com.dynamis.sep_api.escrow.domain.model.ContaEscrow;
import com.dynamis.sep_api.escrow.domain.model.MovimentacaoEscrow;
import com.dynamis.sep_api.escrow.domain.model.Wallet;
import com.dynamis.sep_api.escrow.domain.vo.StatusContaEscrow;
import com.dynamis.sep_api.escrow.domain.vo.StatusMovimentacao;
import com.dynamis.sep_api.escrow.domain.vo.TipoWallet;
import com.dynamis.sep_api.escrow.infrastructure.persistence.MovimentacaoEscrowRepository;
import com.dynamis.sep_api.escrow.infrastructure.persistence.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Testes da reconciliacao do aporte no escrow (Sprint 29 Task 29.5): credito de wallet somente na
 * primeira liquidacao, falha sem credito e protecao de tipo/estado.
 */
class ReconciliarAporteEscrowUseCaseTest {

    private MovimentacaoEscrowRepository movimentacaoRepository;
    private WalletRepository walletRepository;
    private ReconciliarAporteEscrowUseCase useCase;

    private Wallet wallet;
    private MovimentacaoEscrow aporte;
    private final UUID movimentacaoId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        movimentacaoRepository = mock(MovimentacaoEscrowRepository.class);
        walletRepository = mock(WalletRepository.class);
        useCase = new ReconciliarAporteEscrowUseCase(movimentacaoRepository, walletRepository);

        ContaEscrow conta = ContaEscrow.criar("SEP-COBRANCA", StatusContaEscrow.ATIVA);
        UUID propostaId = UUID.randomUUID();
        wallet = Wallet.criar(conta, propostaId, TipoWallet.PROPOSTA);
        aporte = MovimentacaoEscrow.criarAporte(
                wallet, new BigDecimal("2500.00"), "aporte:key-1", OffsetDateTime.now(), UUID.randomUUID());
        when(movimentacaoRepository.findByIdForUpdate(movimentacaoId)).thenReturn(Optional.of(aporte));
        when(walletRepository.findByPropostaIdForUpdate(propostaId)).thenReturn(Optional.of(wallet));
    }

    @Test
    void liquidarTransicionaECreditaWalletUmaVez() {
        MovimentacaoEscrow liquidada = useCase.liquidar(movimentacaoId);

        assertThat(liquidada.getStatus()).isEqualTo(StatusMovimentacao.LIQUIDADA);
        assertThat(wallet.getSaldo()).isEqualByComparingTo("2500.00");

        // replay idempotente: sem novo credito
        useCase.liquidar(movimentacaoId);
        assertThat(wallet.getSaldo()).isEqualByComparingTo("2500.00");
    }

    @Test
    void falharTransicionaSemCreditarWallet() {
        MovimentacaoEscrow falhada = useCase.falhar(movimentacaoId);

        assertThat(falhada.getStatus()).isEqualTo(StatusMovimentacao.FALHOU);
        assertThat(wallet.getSaldo()).isEqualByComparingTo("0.00");

        // replay idempotente
        useCase.falhar(movimentacaoId);
        assertThat(wallet.getSaldo()).isEqualByComparingTo("0.00");
    }

    @Test
    void transicaoConflitanteAposTerminalFalha() {
        useCase.liquidar(movimentacaoId);

        assertThatIllegalStateException().isThrownBy(() -> useCase.falhar(movimentacaoId));
        assertThat(aporte.getStatus()).isEqualTo(StatusMovimentacao.LIQUIDADA);
        assertThat(wallet.getSaldo()).isEqualByComparingTo("2500.00");
    }

    @Test
    void movimentacaoQueNaoEAporteERejeitada() {
        MovimentacaoEscrow recebimento = MovimentacaoEscrow.criarRecebimento(
                wallet, new BigDecimal("100.00"), "rec-key-1", OffsetDateTime.now(), UUID.randomUUID());
        UUID recebimentoId = UUID.randomUUID();
        when(movimentacaoRepository.findByIdForUpdate(recebimentoId)).thenReturn(Optional.of(recebimento));

        assertThatIllegalStateException().isThrownBy(() -> useCase.liquidar(recebimentoId));
        assertThatIllegalStateException().isThrownBy(() -> useCase.falhar(recebimentoId));
    }

    @Test
    void movimentacaoInexistenteFalha() {
        UUID desconhecida = UUID.randomUUID();
        when(movimentacaoRepository.findByIdForUpdate(desconhecida)).thenReturn(Optional.empty());

        assertThatIllegalStateException().isThrownBy(() -> useCase.liquidar(desconhecida));
    }
}
