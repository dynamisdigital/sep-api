package com.dynamis.sep_api.usuarios.infrastructure.persistence;

import com.dynamis.sep_api.shared.audit.AuditorAwareImpl;
import com.dynamis.sep_api.shared.audit.JpaAuditingConfig;
import com.dynamis.sep_api.usuarios.domain.model.Role;
import com.dynamis.sep_api.usuarios.domain.model.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Slice JPA do {@link UsuarioRepository} contra Postgres local via Docker Compose
 * (profile {@code dev}). Pre-requisito de execucao: {@code docker compose up -d postgres}.
 *
 * <p>Sprint 1 documentou em {@code SmokeBootTest} desvio temporario de Testcontainers
 * (issue Docker Engine 28+); este teste segue o mesmo padrao para manter consistencia.
 * Migracao para Testcontainers fica pendente como follow-up cross-sprint.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, AuditorAwareImpl.class})
@ActiveProfiles("dev")
class UsuarioRepositoryTest {

    @Autowired
    private UsuarioRepository repository;

    @BeforeEach
    void limparTabela() {
        repository.deleteAll();
        repository.flush();
    }

    @Test
    void persistirERecuperarUsuarioPreservaCampos() {
        Usuario novo = Usuario.criar("admin@sep.test", "hash-bcrypt-fake", Role.ADMIN);

        Usuario salvo = repository.saveAndFlush(novo);
        Usuario recarregado = repository.findById(salvo.getId()).orElseThrow();

        assertThat(recarregado.getUsername()).isEqualTo("admin@sep.test");
        assertThat(recarregado.getRole()).isEqualTo(Role.ADMIN);
        assertThat(recarregado.getPassword()).isEqualTo("hash-bcrypt-fake");
    }

    @Test
    void findByUsernameRetornaOptionalPopuladoQuandoUsuarioExiste() {
        repository.saveAndFlush(Usuario.criar("cliente@sep.test", "hash", Role.CLIENTE));

        assertThat(repository.findByUsername("cliente@sep.test")).isPresent();
        assertThat(repository.findByUsername("nao@existe.test")).isEmpty();
    }

    @Test
    void existsByUsernameRetornaTrueParaExistenteEFalseParaInexistente() {
        repository.saveAndFlush(Usuario.criar("operador@sep.test", "hash", Role.ADMIN));

        assertThat(repository.existsByUsername("operador@sep.test")).isTrue();
        assertThat(repository.existsByUsername("outro@sep.test")).isFalse();
    }

    @Test
    void auditoriaPreenchidaAutomaticamenteComFallbackSystem() {
        Usuario salvo = repository.saveAndFlush(Usuario.criar("auditoria@sep.test", "hash", Role.ADMIN));

        assertThat(salvo.getDataCriacao()).isNotNull();
        assertThat(salvo.getDataModificacao()).isNotNull();
        assertThat(salvo.getCriadoPor()).isEqualTo(AuditorAwareImpl.SYSTEM);
        assertThat(salvo.getModificadoPor()).isEqualTo(AuditorAwareImpl.SYSTEM);
    }
}
