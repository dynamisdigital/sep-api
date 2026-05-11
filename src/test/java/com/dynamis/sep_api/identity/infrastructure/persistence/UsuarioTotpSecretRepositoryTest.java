package com.dynamis.sep_api.identity.infrastructure.persistence;

import com.dynamis.sep_api.identity.domain.model.MfaStatus;
import com.dynamis.sep_api.identity.domain.model.UsuarioTotpSecret;
import com.dynamis.sep_api.shared.audit.AuditorAwareImpl;
import com.dynamis.sep_api.shared.audit.JpaAuditingConfig;
import com.dynamis.sep_api.usuarios.domain.model.Role;
import com.dynamis.sep_api.usuarios.domain.model.Usuario;
import com.dynamis.sep_api.usuarios.infrastructure.persistence.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Slice JPA do {@link UsuarioTotpSecretRepository} contra Postgres local via Docker Compose. Segue
 * o mesmo desvio da Sprint 1 (Testcontainers ainda nao reativado).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, AuditorAwareImpl.class})
@ActiveProfiles("dev")
class UsuarioTotpSecretRepositoryTest {

    @Autowired
    private UsuarioTotpSecretRepository repository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private UUID usuarioId;

    @BeforeEach
    void prepararUsuario() {
        repository.deleteAll();
        usuarioRepository.deleteAll();
        Usuario usuario = usuarioRepository.saveAndFlush(
                Usuario.criar("totp-" + UUID.randomUUID() + "@sep.test", "hash", Role.CLIENTE));
        this.usuarioId = usuario.getId();
    }

    @Test
    void persistirTotpEBuscarPorUsuario() {
        UsuarioTotpSecret novo = UsuarioTotpSecret.iniciar(usuarioId, "secret-cifrado-fake");

        UsuarioTotpSecret salvo = repository.saveAndFlush(novo);

        assertThat(repository.findByUsuarioId(usuarioId)).isPresent();
        assertThat(salvo.getStatus()).isEqualTo(MfaStatus.PENDENTE);
        assertThat(salvo.getDataCriacao()).isNotNull();
    }

    @Test
    void ativarTotpAtualizaStatusEDataAtivacao() {
        UsuarioTotpSecret salvo = repository.saveAndFlush(UsuarioTotpSecret.iniciar(usuarioId, "secret-cifrado-fake"));

        salvo.ativar();
        UsuarioTotpSecret atualizado = repository.saveAndFlush(salvo);

        assertThat(atualizado.getStatus()).isEqualTo(MfaStatus.ATIVO);
        assertThat(atualizado.getDataAtivacao()).isNotNull();
    }

    @Test
    void existsByUsuarioIdRespondeCorretamente() {
        assertThat(repository.existsByUsuarioId(usuarioId)).isFalse();

        repository.saveAndFlush(UsuarioTotpSecret.iniciar(usuarioId, "secret"));

        assertThat(repository.existsByUsuarioId(usuarioId)).isTrue();
    }
}
