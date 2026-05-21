package com.dynamis.sep_api.contratos.domain.vo;

import java.util.Set;

/**
 * Estados do envelope de assinatura digital (Sprint 11).
 *
 * <pre>
 *   RASCUNHO    -> ENVIADO (apos provider aceitar o documento)
 *   ENVIADO     -> VISUALIZADO | ASSINADO | RECUSADO | EXPIRADO
 *   VISUALIZADO -> ASSINADO | RECUSADO | EXPIRADO  (historico/auditoria)
 *   ASSINADO / RECUSADO / EXPIRADO = finais
 * </pre>
 */
public enum StatusEnvelope {
    RASCUNHO,
    ENVIADO,
    VISUALIZADO,
    ASSINADO,
    RECUSADO,
    EXPIRADO;

    private static final Set<StatusEnvelope> FINAIS = Set.of(ASSINADO, RECUSADO, EXPIRADO);

    public boolean isFinal() {
        return FINAIS.contains(this);
    }
}
