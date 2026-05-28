package com.dynamis.sep_api.identity.infrastructure.security;

import com.dynamis.sep_api.usuarios.domain.model.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Principal autenticado por JWT. Carrega o UUID do usuario, o e-mail, o conjunto de {@link Role}
 * (Sprint 18 — multi-role) e o flag {@code passwordResetRequired} (follow-up 5F-FIX-04 da Sprint
 * 5), evitando round-trip ao banco em rotas autenticadas. {@link #role()} mantem a role principal
 * (maior precedencia) para compatibilidade; {@link #roles()} e o conjunto completo.
 *
 * <p>Usado pelo {@link JwtAuthenticationFilter} como principal do {@code Authentication} populado
 * no {@code SecurityContextHolder}; o {@code AuditorAwareImpl} reconhece este tipo e extrai
 * {@link #id()} para auditoria.
 *
 * <p>Quando {@code passwordResetRequired} for {@code true}, o {@link
 * PasswordResetEnforcementFilter} bloqueia rotas que nao sejam {@code GET /api/v1/auth/me}, {@code
 * PATCH /api/v1/usuarios/{id}/senha} ou {@code POST /api/v1/auth/logout}.
 */
public record UsuarioAutenticado(UUID id, String username, Role role, Set<Role> roles, boolean passwordResetRequired)
        implements UserDetails {

    /** Normaliza o conjunto de roles (nao nulo/nao vazio, copia defensiva). */
    public UsuarioAutenticado {
        if (roles == null || roles.isEmpty()) {
            roles = EnumSet.of(role);
        } else {
            roles = EnumSet.copyOf(roles);
        }
    }

    /** Construtor de conveniencia single-role mantido para chamadas legadas/testes. */
    public UsuarioAutenticado(UUID id, String username, Role role) {
        this(id, username, role, false);
    }

    /** Construtor de conveniencia single-role com flag de reset. */
    public UsuarioAutenticado(UUID id, String username, Role role, boolean passwordResetRequired) {
        this(id, username, role, EnumSet.of(role), passwordResetRequired);
    }

    /** Construtor multi-role: deriva a role principal por precedencia. */
    public UsuarioAutenticado(UUID id, String username, Set<Role> roles, boolean passwordResetRequired) {
        this(id, username, Role.principalDe(roles), roles, passwordResetRequired);
    }

    /** {@code true} se o usuario possui a role informada (verificacao multi-role). */
    public boolean temRole(Role role) {
        return roles.contains(role);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream()
                .map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r.name()))
                .toList();
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
