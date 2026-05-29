package com.dynamis.sep_api.escrow.application.port.out.dto;

import com.dynamis.sep_api.escrow.domain.vo.TipoWallet;

import java.util.UUID;

/**
 * Comando para criar uma wallet sob uma conta escrow no provedor (Epic 15 / Sprint 19).
 *
 * @param contaEscrowExternalId id externo da conta escrow no provider
 * @param propostaId proposta associada (pode ser nulo para wallets nao vinculadas a proposta)
 * @param tipoWallet tipo de wallet de dominio
 */
public record ComandoCriarWallet(String contaEscrowExternalId, UUID propostaId, TipoWallet tipoWallet) {}
