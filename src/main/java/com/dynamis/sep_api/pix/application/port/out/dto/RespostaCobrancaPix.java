package com.dynamis.sep_api.pix.application.port.out.dto;

/**
 * Resposta do provider Pix para a criacao de uma cobranca de recebimento (Sprint 21 Task 21.2).
 * O provider ecoa o {@code txid} controlado pelo SEP, devolve seu proprio id de cobranca
 * ({@code providerReferenciaId}) e o {@code codigoCopiaCola} (EMV/QR) quando houver. Sem JSON bruto
 * nem tipos HTTP.
 */
public record RespostaCobrancaPix(String txid, String providerReferenciaId, String codigoCopiaCola) {}
