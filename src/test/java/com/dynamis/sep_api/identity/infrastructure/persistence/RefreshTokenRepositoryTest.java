package com.dynamis.sep_api.identity.infrastructure.persistence;

import com.dynamis.sep_api.identity.domain.model.RefreshToken;
import com.dynamis.sep_api.identity.domain.model.RefreshTokenStatus;
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
class RefreshTokenRepositoryTest {

    @Autowired
    private RefreshTokenRepository repository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private UUID usuarioId;

    @BeforeEach
    void prepararUsuario() {
        repository.deleteAll();
        usuarioRepository.deleteAll();
        Usuario usuario = usuarioRepository.saveAndFlush(
                Usuario.criar("refresh-" + UUID.randomUUID() + "@sep.test", "hash", Role.CLIENTE));
        this.usuarioId = usuario.getId();
    }

    @Test
    void persistirEBuscarPorHash() {
        RefreshToken token = RefreshToken.emitirNovoLogin(
                usuarioId, "hash-token-1", OffsetDateTime.now().plusDays(30));

        repository.saveAndFlush(token);

        assertThat(repository.findByTokenHash("hash-token-1")).isPresent();
        assertThat(repository.findByTokenHash("inexistente")).isEmpty();
    }

    @Test
    void buscarTokensDaFamilia() {
        RefreshToken pai = RefreshToken.emitirNovoLogin(
                usuarioId, "hash-pai", OffsetDateTime.now().plusDays(30));
        repository.saveAndFlush(pai);

        RefreshToken filho = RefreshToken.emitir(
                usuarioId, pai.getFamilyId(), "hash-filho", OffsetDateTime.now().plusDays(30));
        repository.saveAndFlush(filho);

        assertThat(repository.findByFamilyId(pai.getFamilyId())).hasSize(2);
    }

    @Test
    void revogarFamiliaMudaStatusDeTodos() {
        RefreshToken pai = RefreshToken.emitirNovoLogin(
                usuarioId, "hash-pai", OffsetDateTime.now().plusDays(30));
        repository.saveAndFlush(pai);
        RefreshToken filho = RefreshToken.emitir(
                usuarioId, pai.getFamilyId(), "hash-filho", OffsetDateTime.now().plusDays(30));
        repository.saveAndFlush(filho);

        int afetados = repository.revogarFamilia(pai.getFamilyId(), OffsetDateTime.now());
        repository.flush();

        assertThat(afetados).isEqualTo(2);
        repository.findByFamilyId(pai.getFamilyId()).forEach(t -> assertThat(t.getStatus())
                .isEqualTo(RefreshTokenStatus.REVOGADO));
    }

    @Test
    void revogarTodosDoUsuarioMudaStatus() {
        RefreshToken um = RefreshToken.emitirNovoLogin(
                usuarioId, "hash-um", OffsetDateTime.now().plusDays(30));
        repository.saveAndFlush(um);
        RefreshToken dois = RefreshToken.emitirNovoLogin(
                usuarioId, "hash-dois", OffsetDateTime.now().plusDays(30));
        repository.saveAndFlush(dois);

        int afetados = repository.revogarTodosDoUsuario(usuarioId, OffsetDateTime.now());
        repository.flush();

        assertThat(afetados).isEqualTo(2);
        assertThat(repository.findByUsuarioIdAndStatus(usuarioId, RefreshTokenStatus.ATIVO))
                .isEmpty();
        assertThat(repository.findByUsuarioIdAndStatus(usuarioId, RefreshTokenStatus.REVOGADO))
                .hasSize(2);
    }

    @Test
    void tokenEstaAtivoQuandoSemUsoEAntesDaExpiracao() {
        RefreshToken ativo = RefreshToken.emitirNovoLogin(
                usuarioId, "hash-ativo", OffsetDateTime.now().plusDays(1));

        assertThat(ativo.estaAtivo()).isTrue();
    }

    @Test
    void marcarUsadoSeAtivoVenceApenasUmaVez() {
        // 5F-FIX-06: a transicao ATIVO -> USADO deve afetar exatamente 1 linha; uma segunda
        // chamada no mesmo hash retorna 0 porque o status ja nao e ATIVO. Isso impede que duas
        // chamadas concorrentes apresentando o mesmo refresh recebam dois tokens validos.
        RefreshToken ativo = RefreshToken.emitirNovoLogin(
                usuarioId, "hash-corrida", OffsetDateTime.now().plusDays(30));
        repository.saveAndFlush(ativo);

        int primeira = repository.marcarUsadoSeAtivo("hash-corrida", OffsetDateTime.now());
        repository.flush();
        int segunda = repository.marcarUsadoSeAtivo("hash-corrida", OffsetDateTime.now());
        repository.flush();

        assertThat(primeira).isEqualTo(1);
        assertThat(segunda).isEqualTo(0);
        RefreshToken persistido = repository.findByTokenHash("hash-corrida").orElseThrow();
        assertThat(persistido.getStatus()).isEqualTo(RefreshTokenStatus.USADO);
        assertThat(persistido.getUsadoEm()).isNotNull();
    }

    @Test
    void marcarUsadoSeAtivoNaoAfetaTokenNaoAtivo() {
        // Token ja USADO ou REVOGADO nao sofre transicao via update condicional.
        RefreshToken revogado = RefreshToken.emitirNovoLogin(
                usuarioId, "hash-revogado", OffsetDateTime.now().plusDays(30));
        revogado.revogar();
        repository.saveAndFlush(revogado);

        int afetados = repository.marcarUsadoSeAtivo("hash-revogado", OffsetDateTime.now());

        assertThat(afetados).isEqualTo(0);
        assertThat(repository.findByTokenHash("hash-revogado").orElseThrow().getStatus())
                .isEqualTo(RefreshTokenStatus.REVOGADO);
    }
}
