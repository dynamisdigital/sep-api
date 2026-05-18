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
 * </ul>
 */
public enum Role {
    ADMIN,
    CLIENTE,
    FINANCEIRO
}
