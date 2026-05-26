package com.dynamis.sep_api.usuarios.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoleTest {

    @Test
    void contemValoresEsperados() {
        assertThat(Role.values())
                .containsExactly(Role.ADMIN, Role.CLIENTE, Role.FINANCEIRO, Role.BACKOFFICE);
    }

    @Test
    void backoffice_existe() {
        assertThat(Role.valueOf("BACKOFFICE")).isEqualTo(Role.BACKOFFICE);
    }
}
