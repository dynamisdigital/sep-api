package com.dynamis.sep_api.pix.application.port.out;

import com.dynamis.sep_api.pix.application.port.out.dto.ContaOperacionalEscrowView;

import java.util.Optional;

/**
 * Porta de saida para resolver a conta operacional/escrow ativa do SEP (Sprint 31): a conta dona
 * das chaves Pix geridas de forma assistida. Implementada por adapter em
 * {@code pix.infrastructure.adapter.escrow} — o modulo {@code pix} nao acessa repository de outro
 * modulo diretamente.
 */
public interface ContaOperacionalEscrowQueryPort {

    /** Conta operacional ativa, ou vazio quando ainda nao existe/nao esta ATIVA. */
    Optional<ContaOperacionalEscrowView> buscarContaOperacionalAtiva();
}
