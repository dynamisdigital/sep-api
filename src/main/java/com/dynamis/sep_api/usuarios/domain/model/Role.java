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
    BACKOFFICE;

    /**
     * Precedencia para derivar a role principal (denormalizada) de um conjunto cumulativo de roles
     * (Sprint 18). Quanto mais cedo na lista, maior a precedencia.
     */
    private static final java.util.List<Role> PRECEDENCIA = java.util.List.of(ADMIN, FINANCEIRO, BACKOFFICE, CLIENTE);

    /** Role de maior precedencia presente no conjunto. Lanca se o conjunto for vazio/invalido. */
    public static Role principalDe(java.util.Set<Role> roles) {
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("conjunto de roles vazio");
        }
        return PRECEDENCIA.stream()
                .filter(roles::contains)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("conjunto de roles invalido: " + roles));
    }
}
