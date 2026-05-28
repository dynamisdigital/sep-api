package com.dynamis.sep_api.usuarios.infrastructure.persistence;

import com.dynamis.sep_api.usuarios.domain.model.Role;
import com.dynamis.sep_api.usuarios.domain.model.Usuario;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.EnumSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Round-trip de persistencia das roles cumulativas (Sprint 18 Task 18.2). Prova que mutacao
 * in-place de {@code roles} (@ElementCollection) persiste adicoes E remocoes, e que o principal
 * denormalizado (`usuario.role`) acompanha apos reload.
 */
@SpringBootTest
@ActiveProfiles("test")
class UsuarioRolesPersistenceIT {

    @Autowired
    UsuarioRepository usuarioRepository;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    Environment environment;

    @BeforeEach
    @AfterEach
    void limpar() {
        String url = environment.getProperty("spring.datasource.url", "");
        if (!url.contains("sep_test")) {
            throw new IllegalStateException("UsuarioRolesPersistenceIT deve rodar apenas no banco sep_test");
        }
        usuarioRepository.deleteAll();
    }

    @Test
    void persisteAdicaoESubstituicaoDeRoles() {
        UUID id = tx.execute(s -> {
            Usuario u = usuarioRepository.saveAndFlush(
                    Usuario.criar("multi-" + UUID.randomUUID() + "@sep.test", "hash", Role.FINANCEIRO));
            return u.getId();
        });

        // adiciona BACKOFFICE -> conjunto {FINANCEIRO, BACKOFFICE}
        tx.executeWithoutResult(s -> {
            Usuario u = usuarioRepository.findById(id).orElseThrow();
            u.adicionarRole(Role.BACKOFFICE);
            usuarioRepository.saveAndFlush(u);
        });
        tx.executeWithoutResult(s -> {
            Usuario u = usuarioRepository.findById(id).orElseThrow();
            assertThat(u.getRoles()).containsExactlyInAnyOrder(Role.FINANCEIRO, Role.BACKOFFICE);
            assertThat(u.getRole()).isEqualTo(Role.FINANCEIRO);
        });

        // substitui por {BACKOFFICE} -> remocao de FINANCEIRO deve persistir
        tx.executeWithoutResult(s -> {
            Usuario u = usuarioRepository.findById(id).orElseThrow();
            u.substituirRoles(EnumSet.of(Role.BACKOFFICE));
            usuarioRepository.saveAndFlush(u);
        });
        tx.executeWithoutResult(s -> {
            Usuario u = usuarioRepository.findById(id).orElseThrow();
            assertThat(u.getRoles()).containsExactly(Role.BACKOFFICE);
            assertThat(u.getRole()).isEqualTo(Role.BACKOFFICE);
        });
    }
}
