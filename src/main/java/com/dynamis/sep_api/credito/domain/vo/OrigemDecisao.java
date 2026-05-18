package com.dynamis.sep_api.credito.domain.vo;

/**
 * Origem da {@code DecisaoCredito} final: {@link #MOTOR} quando o motor de regras decidiu
 * automaticamente (rejeicao direta por bloqueio absoluto) ou {@link #MANUAL} quando operador
 * financeiro registrou parecer final.
 */
public enum OrigemDecisao {
    MOTOR,
    MANUAL
}
