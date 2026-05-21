package com.dynamis.sep_api.contratos.domain.event;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Disparado quando o callback do provider confirma assinatura do contrato (Sprint 11 Task 11.5).
 * Consumido pelo modulo {@code cobranca} (Sprint 12) pra gerar agenda de parcelas.
 *
 * <p>{@code dataAssinatura} eh o timestamp do provider externo (Sprint 11 Task 11.8 — exigencia do
 * spec §11.8: audit log {@code ASSINATURA_ASSINADA} precisa registrar o momento real da assinatura
 * pra trilha legal, nao o instante em que o callback foi recebido pelo SEP).
 */
public record ContratoAssinadoEvent(
        UUID contratoId,
        UUID propostaId,
        UUID tomadorId,
        UUID versaoId,
        UUID envelopeId,
        UUID documentoAssinadoId,
        String hashPdfAssinado,
        OffsetDateTime dataAssinatura) {}
