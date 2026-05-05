package com.dynamis.sep_api.shared.audit;

import com.dynamis.sep_api.identity.infrastructure.security.UsuarioAutenticado;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Provedor do auditor (criadoPor/modificadoPor) para o JPA Auditing.
 *
 * <p>PRD §15: prioriza o UUID do usuario autenticado, com fallback {@code "system"} quando nao
 * ha autenticacao real no contexto. Sprint 3 reconhece {@link UsuarioAutenticado} como
 * principal vindo do filtro JWT e extrai o UUID via {@link UsuarioAutenticado#id()}.
 */
@Component("auditorAware")
public class AuditorAwareImpl implements AuditorAware<String> {

    public static final String SYSTEM = "system";

    @Override
    public Optional<String> getCurrentAuditor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return Optional.of(SYSTEM);
        }
        if (auth.getPrincipal() instanceof UsuarioAutenticado principal) {
            return Optional.of(principal.id().toString());
        }
        String name = auth.getName();
        return Optional.of(name == null || name.isBlank() ? SYSTEM : name);
    }
}
