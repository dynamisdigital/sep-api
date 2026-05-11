package com.dynamis.sep_api.identity.domain.model;

/**
 * Estados do refresh token rotativo (Sprint 5 Task 5.3).
 *
 * <ul>
 *   <li>{@link #ATIVO}: pode ser apresentado em /auth/refresh.
 *   <li>{@link #USADO}: ja foi rotacionado; re-uso configura reuse detection e revoga toda a
 *       familia.
 *   <li>{@link #REVOGADO}: logout, logout-all ou reuse detection.
 *   <li>{@link #EXPIRADO}: TTL ultrapassado (housekeeping marca apos expira_em).
 * </ul>
 */
public enum RefreshTokenStatus {
    ATIVO,
    USADO,
    REVOGADO,
    EXPIRADO
}
