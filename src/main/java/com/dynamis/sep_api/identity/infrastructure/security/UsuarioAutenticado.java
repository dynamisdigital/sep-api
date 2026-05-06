package com.dynamis.sep_api.identity.infrastructure.security;

import com.dynamis.sep_api.usuarios.domain.model.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Principal autenticado por JWT. Carrega o UUID do usuario, o e-mail e o {@link Role}, evitando
 * round-trip ao banco em rotas autenticadas. Usado pelo {@link JwtAuthenticationFilter} como
 * principal do {@code Authentication} populado no {@code SecurityContextHolder}; o
 * {@code AuditorAwareImpl} reconhece este tipo e extrai {@link #id()} para auditoria.
 */
public record UsuarioAutenticado(UUID id, String username, Role role) implements UserDetails {

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
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
