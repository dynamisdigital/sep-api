package com.dynamis.sep_api.identity.application.service;

import com.dynamis.sep_api.identity.domain.model.UsuarioBackupCode;
import com.dynamis.sep_api.identity.infrastructure.persistence.UsuarioBackupCodeRepository;
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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, AuditorAwareImpl.class, BackupCodeService.class})
@ActiveProfiles("dev")
class BackupCodeServiceTest {

    @Autowired
    private BackupCodeService service;

    @Autowired
    private UsuarioBackupCodeRepository repository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private UUID usuarioId;

    @BeforeEach
    void preparar() {
        repository.deleteAll();
        usuarioRepository.deleteAll();
        Usuario usuario = usuarioRepository.saveAndFlush(
                Usuario.criar("bk-" + UUID.randomUUID() + "@sep.test", "hash", Role.CLIENTE));
        this.usuarioId = usuario.getId();
    }

    @Test
    void gerarParaUsuarioRetorna10CodigosClarosEPersisteHashes() {
        List<String> claros = service.gerarParaUsuario(usuarioId);

        assertThat(claros).hasSize(BackupCodeService.QUANTIDADE);
        claros.forEach(c -> assertThat(c).hasSize(BackupCodeService.TAMANHO_CODIGO));

        List<UsuarioBackupCode> persistidos = repository.findByUsuarioId(usuarioId);
        assertThat(persistidos).hasSize(BackupCodeService.QUANTIDADE);
        persistidos.forEach(p -> {
            assertThat(p.isUsado()).isFalse();
            assertThat(p.getCodigoHash()).startsWith("$2a$");
        });
    }

    @Test
    void consumirAceitaCodigoValidoUmaVez() {
        List<String> claros = service.gerarParaUsuario(usuarioId);
        String codigo = claros.get(0);

        assertThat(service.consumir(usuarioId, codigo)).isTrue();
        assertThat(service.consumir(usuarioId, codigo)).isFalse();
        assertThat(repository.countByUsuarioIdAndUsadoFalse(usuarioId)).isEqualTo(BackupCodeService.QUANTIDADE - 1);
    }

    @Test
    void consumirRejeitaCodigoInvalido() {
        service.gerarParaUsuario(usuarioId);

        assertThat(service.consumir(usuarioId, "CODIGO00")).isFalse();
        assertThat(service.consumir(usuarioId, null)).isFalse();
        assertThat(service.consumir(usuarioId, "")).isFalse();
    }

    @Test
    void gerarSegundaVezDescartaCodigosAnteriores() {
        List<String> antigos = service.gerarParaUsuario(usuarioId);
        service.gerarParaUsuario(usuarioId);

        assertThat(repository.findByUsuarioId(usuarioId)).hasSize(BackupCodeService.QUANTIDADE);
        antigos.forEach(c -> assertThat(service.consumir(usuarioId, c)).isFalse());
    }
}
