package com.dynamis.sep_api.escrow.application.port.out;

import com.dynamis.sep_api.escrow.application.port.out.dto.ComandoCriarContaEscrow;
import com.dynamis.sep_api.escrow.application.port.out.dto.ComandoCriarWallet;
import com.dynamis.sep_api.escrow.application.port.out.dto.RespostaContaEscrow;
import com.dynamis.sep_api.escrow.application.port.out.dto.RespostaWallet;

import java.math.BigDecimal;

/**
 * Port de saida para o provedor de conta escrow (Provider Pattern, ADR 0004 + ADR 0005; Epic 15 /
 * Sprint 19). Cobre as capacidades Celcoin basicas necessarias a foundation Pix: criar/consultar
 * conta escrow, criar/consultar wallet e consultar saldo.
 *
 * <ul>
 *   <li>{@code FakeEscrowProvider} — dev/test sem credenciais Celcoin; deterministico.
 *   <li>{@code CelcoinEscrowProvider} — adapter HTTP real com OAuth2 + Resilience4j.
 * </ul>
 *
 * <p>Selecao por {@code app.escrow.provider} ({@code fake} ou {@code celcoin}). Este port NAO
 * substitui {@code RegistrarMovimentacaoEscrowUseCase} (comportamento local da Sprint 12, mantido):
 * trata apenas da fronteira com o provedor externo. DTOs falam linguagem de dominio SEP.
 */
public interface EscrowProvider {

    /**
     * Cria uma conta escrow no provedor. Operacao com efeito externo: recebe {@code idempotencyKey}
     * explicita para deduplicar reenvios. Retorna id externo + status.
     */
    RespostaContaEscrow criarContaEscrow(ComandoCriarContaEscrow comando, String idempotencyKey, String correlationId);

    /** Consulta uma conta escrow pelo id externo do provider. */
    RespostaContaEscrow consultarContaEscrow(String externalId, String correlationId);

    /**
     * Cria uma wallet sob uma conta escrow no provedor. Operacao com efeito externo: recebe {@code
     * idempotencyKey} explicita. Retorna id externo + saldo inicial.
     */
    RespostaWallet criarWallet(ComandoCriarWallet comando, String idempotencyKey, String correlationId);

    /** Consulta uma wallet pelo id externo do provider. */
    RespostaWallet consultarWallet(String externalId, String correlationId);

    /** Consulta o saldo atual de uma wallet pelo id externo do provider. */
    BigDecimal consultarSaldo(String walletExternalId, String correlationId);
}
