package com.dynamis.sep_api.shared.audit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, AuditorAwareImpl.class})
@ActiveProfiles("dev")
class AuditLogSegurancaRepositoryTest {

    @Autowired
    private AuditLogSegurancaRepository repository;

    @BeforeEach
    void limpar() {
        repository.deleteAll();
    }

    @Test
    void persistirEBuscarPorUsuario() {
        UUID usuarioId = UUID.randomUUID();
        repository.saveAndFlush(AuditLogSeguranca.registrar(
                TipoEventoSeguranca.LOGIN_OK, usuarioId, "127.0.0.1", "ua", "{\"info\":\"ok\"}"));
        repository.saveAndFlush(AuditLogSeguranca.registrar(
                TipoEventoSeguranca.LOGIN_FAIL, usuarioId, "127.0.0.1", "ua", "{\"motivo\":\"senha invalida\"}"));

        assertThat(repository.findByUsuarioIdOrderByDataEventoDesc(usuarioId)).hasSize(2);
    }

    @Test
    void buscarPorTipo() {
        UUID usuarioId = UUID.randomUUID();
        repository.saveAndFlush(
                AuditLogSeguranca.registrar(TipoEventoSeguranca.MFA_ENABLED, usuarioId, "127.0.0.1", "ua", "{}"));
        repository.saveAndFlush(AuditLogSeguranca.registrar(
                TipoEventoSeguranca.MFA_ENABLED, UUID.randomUUID(), "127.0.0.1", "ua", "{}"));

        assertThat(repository.findByTipoOrderByDataEventoDesc(TipoEventoSeguranca.MFA_ENABLED))
                .hasSize(2);
    }

    @Test
    void persistirJsonbDetalhes() {
        UUID usuarioId = UUID.randomUUID();
        AuditLogSeguranca evento = repository.saveAndFlush(AuditLogSeguranca.registrar(
                TipoEventoSeguranca.REFRESH_REUSE_DETECTED,
                usuarioId,
                "127.0.0.1",
                "ua",
                "{\"familyId\":\"abc\",\"acao\":\"revogar-familia\"}"));

        AuditLogSeguranca recuperado = repository.findById(evento.getId()).orElseThrow();
        assertThat(recuperado.getDetalhes()).contains("familyId");
        assertThat(recuperado.getTipo()).isEqualTo(TipoEventoSeguranca.REFRESH_REUSE_DETECTED);
    }

    @Test
    void buscarPorUsuarioETipo() {
        UUID usuarioId = UUID.randomUUID();
        repository.saveAndFlush(
                AuditLogSeguranca.registrar(TipoEventoSeguranca.LOCKOUT, usuarioId, "127.0.0.1", "ua", "{}"));
        repository.saveAndFlush(
                AuditLogSeguranca.registrar(TipoEventoSeguranca.LOGIN_OK, usuarioId, "127.0.0.1", "ua", "{}"));

        assertThat(repository.findByUsuarioIdAndTipoOrderByDataEventoDesc(usuarioId, TipoEventoSeguranca.LOCKOUT))
                .hasSize(1);
    }
}
