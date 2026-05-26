package com.dynamis.sep_api.usuarios.domain.model;

/**
 * Perfis suportados pelo SEP.
 *
 * <ul>
 *   <li>{@link #ADMIN}: operador interno com privilegios maximos (cadastros, gestao, configuracao).
 *   <li>{@link #CLIENTE}: tomador ou investidor (jornadas publicas + autenticadas).
 *   <li>{@link #FINANCEIRO}: operador interno do time financeiro (Sprint 8 Task 8.4) — autorizado a
 *       registrar parecer manual de credito ({@code POST /api/v1/credito/propostas/{id}/parecer})
 *       e consultar dados operacionais de credito. Promocao de role exige ADMIN + step-up.
 *   <li>{@link #BACKOFFICE}: operador interno de backoffice operacional (Sprint 14 Task 14.6) —
 *       opera fila operacional, reprocessos manuais e dashboard. Pode operar em conjunto com
 *       {@link #FINANCEIRO} (usuario distinto promovido a ambas) sem que uma role implique a outra.
 *       NAO concede acesso a parecer de credito nem registro de recebimento.
 * </ul>
 */
public enum Role {
    ADMIN,
    CLIENTE,
    FINANCEIRO,
    BACKOFFICE
}
