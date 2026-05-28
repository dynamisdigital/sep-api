package com.dynamis.sep_api.usuarios.domain.model;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Testes de dominio das roles cumulativas do usuario (Sprint 18 Task 18.1). */
class UsuarioRolesTest {

    private Usuario novoCliente() {
        return Usuario.criar("user@sep.test", "hash", Role.CLIENTE);
    }

    @Test
    void criaComRoleUnicaEPrincipalCoerente() {
        Usuario u = novoCliente();
        assertThat(u.getRoles()).containsExactly(Role.CLIENTE);
        assertThat(u.getRole()).isEqualTo(Role.CLIENTE);
        assertThat(u.possuiRole(Role.CLIENTE)).isTrue();
        assertThat(u.possuiRole(Role.ADMIN)).isFalse();
    }

    @Test
    void adicionarRolesAcumulaFinanceiroEBackoffice() {
        Usuario u = Usuario.criar("op@sep.test", "hash", Role.FINANCEIRO);
        u.adicionarRole(Role.BACKOFFICE);
        assertThat(u.getRoles()).containsExactlyInAnyOrder(Role.FINANCEIRO, Role.BACKOFFICE);
        // principal por precedencia: FINANCEIRO > BACKOFFICE
        assertThat(u.getRole()).isEqualTo(Role.FINANCEIRO);
    }

    @Test
    void principalSegueAdminQuandoPresente() {
        Usuario u = Usuario.criar("a@sep.test", "hash", Role.BACKOFFICE);
        u.adicionarRole(Role.ADMIN);
        assertThat(u.getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void substituirRolesTrocaConjuntoEPrincipal() {
        Usuario u = novoCliente();
        u.substituirRoles(EnumSet.of(Role.FINANCEIRO, Role.BACKOFFICE));
        assertThat(u.getRoles()).containsExactlyInAnyOrder(Role.FINANCEIRO, Role.BACKOFFICE);
        assertThat(u.getRole()).isEqualTo(Role.FINANCEIRO);
    }

    @Test
    void alterarRoleLegadoSubstituiConjunto() {
        Usuario u = Usuario.criar("op@sep.test", "hash", Role.FINANCEIRO);
        u.adicionarRole(Role.BACKOFFICE);
        u.alterarRole(Role.ADMIN);
        assertThat(u.getRoles()).containsExactly(Role.ADMIN);
        assertThat(u.getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void removerRoleMantemConjuntoNaoVazio() {
        Usuario u = Usuario.criar("op@sep.test", "hash", Role.FINANCEIRO);
        u.adicionarRole(Role.BACKOFFICE);
        u.removerRole(Role.FINANCEIRO);
        assertThat(u.getRoles()).containsExactly(Role.BACKOFFICE);
        assertThat(u.getRole()).isEqualTo(Role.BACKOFFICE);
    }

    @Test
    void removerUltimaRoleFalha() {
        Usuario u = novoCliente();
        assertThatThrownBy(() -> u.removerRole(Role.CLIENTE)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void substituirPorConjuntoVazioFalha() {
        Usuario u = novoCliente();
        assertThatThrownBy(() -> u.substituirRoles(EnumSet.noneOf(Role.class)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getRolesEhImutavel() {
        Usuario u = novoCliente();
        assertThatThrownBy(() -> u.getRoles().add(Role.ADMIN)).isInstanceOf(UnsupportedOperationException.class);
    }
}
