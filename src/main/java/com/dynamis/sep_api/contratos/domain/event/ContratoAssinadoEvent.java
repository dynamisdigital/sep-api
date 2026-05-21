package com.dynamis.sep_api.contratos.domain.event;

import java.util.UUID;

/**
 * Disparado quando o callback do provider confirma assinatura do contrato (Sprint 11 Task 11.5).
 * Consumido pelo modulo {@code cobranca} (Sprint 12) pra gerar agenda de parcelas.
 */
public record ContratoAssinadoEvent(
        UUID contratoId,
        UUID propostaId,
        UUID tomadorId,
        UUID versaoId,
        UUID envelopeId,
        UUID documentoAssinadoId,
        String hashPdfAssinado) {}
