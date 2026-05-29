package com.dynamis.sep_api.escrow.infrastructure.adapter.fake;

import com.dynamis.sep_api.escrow.application.port.out.dto.ComandoCriarContaEscrow;
import com.dynamis.sep_api.escrow.application.port.out.dto.ComandoCriarWallet;
import com.dynamis.sep_api.escrow.application.port.out.dto.RespostaContaEscrow;
import com.dynamis.sep_api.escrow.application.port.out.dto.RespostaWallet;
import com.dynamis.sep_api.escrow.domain.vo.StatusContaEscrow;
import com.dynamis.sep_api.escrow.domain.vo.TipoWallet;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Testes do {@link FakeEscrowProvider} (dev/test sem credenciais Celcoin). */
class FakeEscrowProviderTest {

    private final FakeEscrowProvider provider = new FakeEscrowProvider();

    @Test
    void criarContaEscrowNasceAtiva() {
        RespostaContaEscrow resp = provider.criarContaEscrow(new ComandoCriarContaEscrow("SEP"), "idem-1", "corr-1");
        assertThat(resp.externalId()).startsWith("fake-escrow-acc-");
        assertThat(resp.status()).isEqualTo(StatusContaEscrow.ATIVA);
    }

    @Test
    void criarWalletNasceComSaldoZero() {
        RespostaWallet resp = provider.criarWallet(
                new ComandoCriarWallet("fake-escrow-acc-1", UUID.randomUUID(), TipoWallet.PROPOSTA), "idem-1", "corr-1");
        assertThat(resp.externalId()).startsWith("fake-escrow-wallet-");
        assertThat(resp.saldo()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void consultarSaldoDevolveZero() {
        assertThat(provider.consultarSaldo("fake-escrow-wallet-1", "corr-1")).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void consultarContaEscrowDevolveAtiva() {
        assertThat(provider.consultarContaEscrow("fake-escrow-acc-1", "corr-1").status())
                .isEqualTo(StatusContaEscrow.ATIVA);
    }
}
