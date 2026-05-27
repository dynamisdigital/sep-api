package com.dynamis.sep_api.escrow.domain.model;

import com.dynamis.sep_api.escrow.domain.vo.StatusContaEscrow;
import com.dynamis.sep_api.escrow.domain.vo.TipoWallet;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cobre branches negativos das factories e operacoes do dominio escrow (Sprint 15 Task 15.10).
 * Alvo: trazer branches de {@code escrow} acima do gate de 70%.
 */
class EscrowDomainValidationTest {

    @Test
    void contaEscrowCriarRejeitaTitularEmBranco() {
        assertThatThrownBy(() -> ContaEscrow.criar("   ", StatusContaEscrow.ATIVA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("titular");
    }

    @Test
    void contaEscrowCriarRejeitaTitularNulo() {
        assertThatThrownBy(() -> ContaEscrow.criar(null, StatusContaEscrow.ATIVA))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void walletCreditarRejeitaValorZero() {
        Wallet wallet =
                Wallet.criar(ContaEscrow.criar("SEP", StatusContaEscrow.ATIVA), UUID.randomUUID(), TipoWallet.PROPOSTA);
        assertThatThrownBy(() -> wallet.creditar(BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positivo");
    }

    @Test
    void walletCreditarRejeitaValorNegativo() {
        Wallet wallet =
                Wallet.criar(ContaEscrow.criar("SEP", StatusContaEscrow.ATIVA), UUID.randomUUID(), TipoWallet.PROPOSTA);
        assertThatThrownBy(() -> wallet.creditar(new BigDecimal("-1.00"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void movimentacaoCriarRecebimentoRejeitaValorNegativo() {
        Wallet wallet =
                Wallet.criar(ContaEscrow.criar("SEP", StatusContaEscrow.ATIVA), UUID.randomUUID(), TipoWallet.PROPOSTA);
        assertThatThrownBy(() -> MovimentacaoEscrow.criarRecebimento(
                        wallet, new BigDecimal("-10.00"), "idem-1", OffsetDateTime.now(), UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positivo");
    }

    @Test
    void movimentacaoCriarRecebimentoRejeitaIdempotencyKeyBlank() {
        Wallet wallet =
                Wallet.criar(ContaEscrow.criar("SEP", StatusContaEscrow.ATIVA), UUID.randomUUID(), TipoWallet.PROPOSTA);
        assertThatThrownBy(() -> MovimentacaoEscrow.criarRecebimento(
                        wallet, new BigDecimal("100.00"), "   ", OffsetDateTime.now(), UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("idempotencyKey");
    }
}
