package com.dynamis.sep_api.identity.infrastructure.persistence;

import com.dynamis.sep_api.identity.domain.model.StepUpToken;
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

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, AuditorAwareImpl.class})
@ActiveProfiles("dev")
class StepUpTokenRepositoryTest {

    @Autowired
    private StepUpTokenRepository repository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private UUID usuarioId;

    @BeforeEach
    void prepararUsuario() {
        repository.deleteAll();
        usuarioRepository.deleteAll();
        Usuario usuario = usuarioRepository.saveAndFlush(
                Usuario.criar("stepup-" + UUID.randomUUID() + "@sep.test", "hash", Role.CLIENTE));
        this.usuarioId = usuario.getId();
    }

    @Test
    void persistirEBuscarPorHash() {
        StepUpToken token = StepUpToken.emitir(
                usuarioId, "hash-stepup-1", OffsetDateTime.now().plusMinutes(5));

        repository.saveAndFlush(token);

        assertThat(repository.findByTokenHash("hash-stepup-1")).isPresent();
    }

    @Test
    void estaValidoQuandoNaoUsadoEDentroDoPrazo() {
        StepUpToken valido = StepUpToken.emitir(
                usuarioId, "hash-valido", OffsetDateTime.now().plusMinutes(5));

        assertThat(valido.estaValido()).isTrue();
    }

    @Test
    void marcarUsadoBloqueiaReuso() {
        StepUpToken token =
                StepUpToken.emitir(usuarioId, "hash-usado", OffsetDateTime.now().plusMinutes(5));

        token.marcarUsado();

        assertThat(token.isUsado()).isTrue();
        assertThat(token.estaValido()).isFalse();
    }
}
