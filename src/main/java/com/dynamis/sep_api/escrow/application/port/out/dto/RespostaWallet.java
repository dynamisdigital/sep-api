package com.dynamis.sep_api.escrow.application.port.out.dto;

import java.math.BigDecimal;

/**
 * Resposta do provider para criacao/consulta de wallet: id externo + saldo atual reportado.
 */
public record RespostaWallet(String externalId, BigDecimal saldo) {}
