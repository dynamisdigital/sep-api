package com.dynamis.sep_api.pix.application.port.out.dto;

import java.util.UUID;

/**
 * Projecao minima da conta operacional/escrow para gestao de chaves Pix (Sprint 31).
 * {@code contaTecnicaId} e o identificador tecnico repassado ao provider (external id quando
 * existir; senao o proprio id local) — nunca dados bancarios.
 */
public record ContaOperacionalEscrowView(UUID contaEscrowId, String contaTecnicaId) {}
