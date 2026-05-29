package com.dynamis.sep_api.escrow.infrastructure.adapter.fake;

import com.dynamis.sep_api.escrow.application.port.out.EscrowProvider;
import com.dynamis.sep_api.escrow.application.port.out.dto.ComandoCriarContaEscrow;
import com.dynamis.sep_api.escrow.application.port.out.dto.ComandoCriarWallet;
import com.dynamis.sep_api.escrow.application.port.out.dto.RespostaContaEscrow;
import com.dynamis.sep_api.escrow.application.port.out.dto.RespostaWallet;
import com.dynamis.sep_api.escrow.domain.vo.StatusContaEscrow;
import com.fasterxml.uuid.Generators;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Adapter fake do {@link EscrowProvider} para dev/test sem credenciais Celcoin (Epic 15 / Sprint
 * 19). Ativado quando {@code app.escrow.provider=fake} (default). Substitui o
 * {@code CelcoinEscrowProvider}.
 *
 * <p>Cenarios deterministicos: contas nascem {@link StatusContaEscrow#ATIVA}; wallets nascem com
 * saldo zero.
 */
@Component
@ConditionalOnProperty(name = "app.escrow.provider", havingValue = "fake", matchIfMissing = true)
public class FakeEscrowProvider implements EscrowProvider {

    private static final Logger log = LoggerFactory.getLogger(FakeEscrowProvider.class);

    @Override
    public RespostaContaEscrow criarContaEscrow(
            ComandoCriarContaEscrow comando, String idempotencyKey, String correlationId) {
        String externalId =
                "fake-escrow-acc-" + Generators.timeBasedReorderedGenerator().generate();
        log.info("FakeEscrowProvider.criarContaEscrow titular={} -> {}", comando.titular(), externalId);
        return new RespostaContaEscrow(externalId, StatusContaEscrow.ATIVA);
    }

    @Override
    public RespostaContaEscrow consultarContaEscrow(String externalId, String correlationId) {
        return new RespostaContaEscrow(externalId, StatusContaEscrow.ATIVA);
    }

    @Override
    public RespostaWallet criarWallet(ComandoCriarWallet comando, String idempotencyKey, String correlationId) {
        String externalId =
                "fake-escrow-wallet-" + Generators.timeBasedReorderedGenerator().generate();
        log.info("FakeEscrowProvider.criarWallet conta={} -> {}", comando.contaEscrowExternalId(), externalId);
        return new RespostaWallet(externalId, BigDecimal.ZERO);
    }

    @Override
    public RespostaWallet consultarWallet(String externalId, String correlationId) {
        return new RespostaWallet(externalId, BigDecimal.ZERO);
    }

    @Override
    public BigDecimal consultarSaldo(String walletExternalId, String correlationId) {
        return BigDecimal.ZERO;
    }
}
