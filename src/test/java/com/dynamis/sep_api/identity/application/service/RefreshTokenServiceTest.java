package com.dynamis.sep_api.identity.application.service;

import com.dynamis.sep_api.identity.application.service.RefreshTokenService.TokenCru;
import com.dynamis.sep_api.identity.domain.model.RefreshToken;
import com.dynamis.sep_api.identity.domain.model.RefreshTokenStatus;
import com.dynamis.sep_api.identity.infrastructure.persistence.RefreshTokenRepository;
import com.dynamis.sep_api.identity.infrastructure.security.JwtProperties;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, AuditorAwareImpl.class, RefreshTokenService.class})
@ActiveProfiles("dev")
class RefreshTokenServiceTest {

    @Autowired
    private RefreshTokenService service;

    @Autowired
    private RefreshTokenRepository repository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @MockitoBean
    private JwtProperties jwtProperties;

    private UUID usuarioId;

    @BeforeEach
    void preparar() {
        repository.deleteAll();
        usuarioRepository.deleteAll();
        Usuario usuario = usuarioRepository.saveAndFlush(
                Usuario.criar("rt-" + UUID.randomUUID() + "@sep.test", "hash", Role.CLIENTE));
        usuarioId = usuario.getId();
        when(jwtProperties.getRefreshExpirationSeconds()).thenReturn(2_592_000L);
    }

    @Test
    void emitirParaNovoLoginCriaTokenAtivoComFamiliaNova() {
        TokenCru cru = service.emitirParaNovoLogin(usuarioId);

        assertThat(cru.token()).isNotBlank();
        RefreshToken persistido = cru.persistido();
        assertThat(persistido.getStatus()).isEqualTo(RefreshTokenStatus.ATIVO);
        assertThat(persistido.getFamilyId()).isNotNull();
        assertThat(persistido.getTokenHash()).isEqualTo(service.hashSha256Hex(cru.token()));
    }

    @Test
    void rotacionarMantemFamilia() {
        TokenCru primeiro = service.emitirParaNovoLogin(usuarioId);
        UUID family = primeiro.persistido().getFamilyId();

        TokenCru segundo = service.rotacionar(usuarioId, family);

        assertThat(segundo.persistido().getFamilyId()).isEqualTo(family);
        assertThat(segundo.token()).isNotEqualTo(primeiro.token());
    }

    @Test
    void hashEhSha256HexDeterministico() {
        String h1 = service.hashSha256Hex("token-x");
        String h2 = service.hashSha256Hex("token-x");

        assertThat(h1).isEqualTo(h2);
        assertThat(h1).hasSize(64);
    }
}
