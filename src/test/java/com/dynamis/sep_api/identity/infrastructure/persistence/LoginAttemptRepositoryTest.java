package com.dynamis.sep_api.identity.infrastructure.persistence;

import com.dynamis.sep_api.identity.domain.model.LoginAttempt;
import com.dynamis.sep_api.identity.domain.model.LoginAttemptStatus;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, AuditorAwareImpl.class})
@ActiveProfiles("dev")
class LoginAttemptRepositoryTest {

    @Autowired
    private LoginAttemptRepository repository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private UUID usuarioId;
    private String username;

    @BeforeEach
    void preparar() {
        repository.deleteAll();
        usuarioRepository.deleteAll();
        this.username = "attempt-" + UUID.randomUUID() + "@sep.test";
        Usuario usuario = usuarioRepository.saveAndFlush(Usuario.criar(username, "hash", Role.CLIENTE));
        this.usuarioId = usuario.getId();
    }

    @Test
    void contarFalhasNaJanela() {
        repository.saveAndFlush(
                LoginAttempt.registrar(usuarioId, username, "127.0.0.1", "ua", LoginAttemptStatus.SENHA_INVALIDA));
        repository.saveAndFlush(
                LoginAttempt.registrar(usuarioId, username, "127.0.0.1", "ua", LoginAttemptStatus.SENHA_INVALIDA));
        repository.saveAndFlush(
                LoginAttempt.registrar(usuarioId, username, "127.0.0.1", "ua", LoginAttemptStatus.SUCESSO));

        long falhas = repository.countByUsernameAndStatusInAndJanela(
                username,
                List.of(LoginAttemptStatus.SENHA_INVALIDA, LoginAttemptStatus.TOTP_INVALIDO),
                OffsetDateTime.now().minusMinutes(15));

        assertThat(falhas).isEqualTo(2);
    }

    @Test
    void contarPorIpNaJanela() {
        String ip = "10.0.0.1";
        repository.saveAndFlush(
                LoginAttempt.registrar(usuarioId, username, ip, "ua", LoginAttemptStatus.SENHA_INVALIDA));
        repository.saveAndFlush(
                LoginAttempt.registrar(null, "outro@sep.test", ip, "ua", LoginAttemptStatus.USUARIO_INEXISTENTE));

        long total = repository.countByIpAndJanela(ip, OffsetDateTime.now().minusMinutes(15));

        assertThat(total).isEqualTo(2);
    }

    @Test
    void buscarInstantesDeFalhaFiltraStatusEOrdenaDoMaisRecente() {
        registrarFalha(LoginAttemptStatus.SENHA_INVALIDA);
        registrarFalha(LoginAttemptStatus.TOTP_INVALIDO);
        registrarFalha(LoginAttemptStatus.SENHA_INVALIDA);
        repository.saveAndFlush(
                LoginAttempt.registrar(usuarioId, username, "127.0.0.1", "ua", LoginAttemptStatus.SUCESSO));

        List<OffsetDateTime> instantes = buscarInstantes(OffsetDateTime.now().minusMinutes(45), 10);

        assertThat(instantes).hasSize(3).isSortedAccordingTo(Comparator.reverseOrder());
    }

    @Test
    void buscarInstantesDeFalhaRespeitaOInicioDaJanela() {
        registrarFalha(LoginAttemptStatus.SENHA_INVALIDA);

        assertThat(buscarInstantes(OffsetDateTime.now().plusMinutes(1), 10)).isEmpty();
    }

    @Test
    void buscarInstantesDeFalhaRespeitaOLimiteDevolvendoOsMaisRecentes() {
        registrarFalha(LoginAttemptStatus.SENHA_INVALIDA);
        registrarFalha(LoginAttemptStatus.SENHA_INVALIDA);
        registrarFalha(LoginAttemptStatus.SENHA_INVALIDA);

        OffsetDateTime inicio = OffsetDateTime.now().minusMinutes(45);
        List<OffsetDateTime> todos = buscarInstantes(inicio, 10);
        List<OffsetDateTime> limitados = buscarInstantes(inicio, 2);

        assertThat(limitados).isEqualTo(todos.subList(0, 2));
    }

    private void registrarFalha(LoginAttemptStatus status) {
        repository.saveAndFlush(LoginAttempt.registrar(usuarioId, username, "127.0.0.1", "ua", status));
    }

    private List<OffsetDateTime> buscarInstantes(OffsetDateTime inicioJanela, int limite) {
        return repository.buscarInstantesDeFalha(
                username,
                List.of(LoginAttemptStatus.SENHA_INVALIDA, LoginAttemptStatus.TOTP_INVALIDO),
                inicioJanela,
                PageRequest.of(0, limite));
    }

    @Test
    void findByUsuarioIdRetornaTentativasDoUsuario() {
        repository.saveAndFlush(
                LoginAttempt.registrar(usuarioId, username, "127.0.0.1", "ua", LoginAttemptStatus.SUCESSO));

        assertThat(repository.findByUsuarioId(usuarioId)).hasSize(1);
    }
}
