package com.dynamis.sep_api.shared.audit;

import org.springframework.data.domain.AuditorAware;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Provedor do auditor (criadoPor/modificadoPor) para o JPA Auditing.
 *
 * <p>Atende PRD §15: prioriza o UUID do usuario autenticado (preenchido pela Sprint 3), com
 * fallback {@code "system"} quando nao ha autenticacao no contexto. Nunca retorna {@link
 * Optional#empty()} para garantir que os campos {@code criado_por} e {@code modificado_por}
 * fiquem sempre populados.
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
        // Sprint 3 vai colocar o UUID do usuario no Authentication.getName().
        // Por ora, qualquer principal autenticado retorna o nome (placeholder).
        String name = auth.getName();
        return Optional.of(name == null || name.isBlank() ? SYSTEM : name);
    }
}
