package com.dynamis.sep_api.pix.application.port.out.dto;

import java.util.UUID;

/**
 * Projecao read-only da wallet/conta escrow da proposta (Sprint 20 Task 20.1). Necessaria para
 * confirmar que existe estrutura escrow operacional antes de liberar desembolso (segregacao
 * patrimonial obrigatoria — CMN 4.656/2018, ADR 0005).
 *
 * @param walletExternalId id externo do provider (Celcoin). Pode vir {@code null} enquanto a conta
 *     escrow for apenas local (Sprint 12 cria conta tecnica sem external id). Desembolso via Celcoin
 *     real depende deste campo; a ausencia eh um gap documentado que bloqueia o provider real.
 * @param operacional {@code true} quando a conta escrow esta {@code ATIVA}.
 */
public record EscrowDesembolsoView(UUID propostaId, UUID walletId, String walletExternalId, boolean operacional) {}
