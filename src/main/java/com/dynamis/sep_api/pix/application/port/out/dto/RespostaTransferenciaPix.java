package com.dynamis.sep_api.pix.application.port.out.dto;

/**
 * Resposta do provider Pix para solicitacao/consulta de transferencia: id externo + status
 * reportado. Sem JSON bruto nem tipos HTTP.
 */
public record RespostaTransferenciaPix(String externalId, StatusTransferenciaPixProvider status) {}
