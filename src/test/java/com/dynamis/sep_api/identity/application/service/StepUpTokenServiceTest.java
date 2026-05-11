package com.dynamis.sep_api.identity.application.service;

import com.dynamis.sep_api.identity.application.service.StepUpTokenService.TokenCru;
import com.dynamis.sep_api.identity.infrastructure.persistence.StepUpTokenRepository;
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

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, AuditorAwareImpl.class, StepUpTokenService.class})
@ActiveProfiles("dev")
class StepUpTokenServiceTest {

    @Autowired
    private StepUpTokenService service;

    @Autowired
    private StepUpTokenRepository repository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private UUID usuarioId;

    @BeforeEach
    void preparar() {
        repository.deleteAll();
        usuarioRepository.deleteAll();
        Usuario usuario = usuarioRepository.saveAndFlush(
                Usuario.criar("step-" + UUID.randomUUID() + "@sep.test", "h", Role.CLIENTE));
        usuarioId = usuario.getId();
    }

    @Test
    void emitirGeraTokenCruEPersisteHash() {
        TokenCru cru = service.emitir(usuarioId);

        assertThat(cru.token()).isNotBlank();
        assertThat(cru.persistido().getTokenHash()).isEqualTo(service.hashSha256Hex(cru.token()));
    }

    @Test
    void validarEConsumirAceitaUmaVez() {
        TokenCru cru = service.emitir(usuarioId);

        assertThat(service.validarEConsumir(cru.token())).contains(usuarioId);
        assertThat(service.validarEConsumir(cru.token())).isEmpty();
    }

    @Test
    void validarEConsumirRejeitaInvalido() {
        assertThat(service.validarEConsumir("invalido")).isEmpty();
        assertThat(service.validarEConsumir(null)).isEmpty();
        assertThat(service.validarEConsumir("")).isEmpty();
    }
}
