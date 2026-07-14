package com.dynamis.sep_api.pix.application.port.out.dto;

/**
 * Resposta do provider ao cadastro de chave Pix (Sprint 31): somente o identificador tecnico da
 * chave no provider. Sem payload bruto, conta bancaria ou valor da chave — o {@code providerKeyId}
 * e interno e nunca entra em DTO publico.
 */
public record RespostaCadastroChavePix(String providerKeyId) {}
