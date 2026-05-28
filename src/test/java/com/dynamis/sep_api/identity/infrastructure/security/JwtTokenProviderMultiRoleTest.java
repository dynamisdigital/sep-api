package com.dynamis.sep_api.identity.infrastructure.security;

import com.dynamis.sep_api.usuarios.domain.model.Role;
import com.dynamis.sep_api.usuarios.domain.model.Usuario;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Garante que o JWT emite e reconstroi TODAS as roles do usuario (Sprint 18 Task 18.3), sem
 * perder a role secundaria de um usuario multi-role (ex.: FINANCEIRO + BACKOFFICE).
 */
class JwtTokenProviderMultiRoleTest {

    private static final String SECRET = "test-secret-com-pelo-menos-256-bits-de-entropia-xyz";

    private final JwtTokenProvider provider = new JwtTokenProvider(SECRET, 900);

    @Test
    void tokenCarregaTodasAsRolesDoUsuarioMultiRole() {
        Usuario u = Usuario.criar("op@sep.test", "hash", Role.FINANCEIRO);
        u.adicionarRole(Role.BACKOFFICE);

        String token = provider.gerarToken(u);
        assertThat(provider.tokenValido(token)).isTrue();

        UsuarioAutenticado principal = provider.extrairPrincipal(token);
        assertThat(principal.roles()).containsExactlyInAnyOrder(Role.FINANCEIRO, Role.BACKOFFICE);
        assertThat(principal.temRole(Role.FINANCEIRO)).isTrue();
        assertThat(principal.temRole(Role.BACKOFFICE)).isTrue();
        assertThat(principal.temRole(Role.ADMIN)).isFalse();
        // principal por precedencia: FINANCEIRO > BACKOFFICE
        assertThat(principal.role()).isEqualTo(Role.FINANCEIRO);
        assertThat(principal.getAuthorities())
                .extracting("authority")
                .containsExactlyInAnyOrder("ROLE_FINANCEIRO", "ROLE_BACKOFFICE");
    }

    @Test
    void tokenSingleRolePermaneceCompativel() {
        Usuario u = Usuario.criar("cli@sep.test", "hash", Role.CLIENTE);

        UsuarioAutenticado principal = provider.extrairPrincipal(provider.gerarToken(u));
        assertThat(principal.roles()).containsExactly(Role.CLIENTE);
        assertThat(principal.role()).isEqualTo(Role.CLIENTE);
        assertThat(principal.getAuthorities()).extracting("authority").containsExactly("ROLE_CLIENTE");
    }
}
