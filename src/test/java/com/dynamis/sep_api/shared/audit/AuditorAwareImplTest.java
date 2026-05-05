package com.dynamis.sep_api.shared.audit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Valida que o {@link AuditorAwareImpl} retorna {@code "system"} quando nao ha autenticacao no
 * contexto, e o nome do principal quando ha.
 */
class AuditorAwareImplTest {

    private final AuditorAwareImpl auditorAware = new AuditorAwareImpl();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void retornaSystemQuandoNaoHaAutenticacao() {
        SecurityContextHolder.clearContext();

        Optional<String> auditor = auditorAware.getCurrentAuditor();

        assertThat(auditor).contains(AuditorAwareImpl.SYSTEM);
    }

    @Test
    void retornaSystemParaAuthAnonima() {
        SecurityContextHolder.getContext()
                .setAuthentication(new AnonymousAuthenticationToken(
                        "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        Optional<String> auditor = auditorAware.getCurrentAuditor();

        assertThat(auditor).contains(AuditorAwareImpl.SYSTEM);
    }

    @Test
    void retornaPrincipalQuandoAutenticado() {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(
                        "1f0799c0-98b9-6d9d-bc4a-7d6f5b771001",
                        null,
                        AuthorityUtils.createAuthorityList("ROLE_CLIENTE")));

        Optional<String> auditor = auditorAware.getCurrentAuditor();

        assertThat(auditor).contains("1f0799c0-98b9-6d9d-bc4a-7d6f5b771001");
    }
}
