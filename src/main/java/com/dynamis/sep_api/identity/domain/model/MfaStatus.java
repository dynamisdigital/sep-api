package com.dynamis.sep_api.identity.domain.model;

/**
 * Estados do TOTP no ciclo de habilitacao (PRD §14, Sprint 5).
 *
 * <ul>
 *   <li>{@link #PENDENTE}: secret gerado mas usuario ainda nao confirmou o primeiro codigo.
 *   <li>{@link #ATIVO}: TOTP habilitado e exigido no login.
 *   <li>{@link #DESABILITADO}: usuario desabilitou explicitamente (mantemos historico).
 * </ul>
 */
public enum MfaStatus {
    PENDENTE,
    ATIVO,
    DESABILITADO
}
