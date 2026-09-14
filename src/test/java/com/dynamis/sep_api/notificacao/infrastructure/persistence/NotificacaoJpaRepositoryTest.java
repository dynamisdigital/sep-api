package com.dynamis.sep_api.notificacao.infrastructure.persistence;

import com.dynamis.sep_api.notificacao.domain.model.Notificacao;
import com.dynamis.sep_api.notificacao.domain.vo.ConteudoNotificacao;
import com.dynamis.sep_api.notificacao.domain.vo.OrigemNotificacao;
import com.dynamis.sep_api.notificacao.domain.vo.Referencia;
import com.dynamis.sep_api.notificacao.domain.vo.SituacaoEntrega;
import com.dynamis.sep_api.notificacao.domain.vo.TipoNotificacao;
import com.dynamis.sep_api.notificacao.domain.vo.TipoReferencia;
import com.dynamis.sep_api.shared.audit.AuditorAwareImpl;
import com.dynamis.sep_api.shared.audit.JpaAuditingConfig;
import com.dynamis.sep_api.usuarios.domain.model.Role;
import com.dynamis.sep_api.usuarios.domain.model.Usuario;
import com.dynamis.sep_api.usuarios.infrastructure.persistence.UsuarioRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Esquema da V61 contra o PostgreSQL: colunas, unique parcial e CHECKs (Sprint 38 Task 38.2). */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, AuditorAwareImpl.class})
@ActiveProfiles("dev")
class NotificacaoJpaRepositoryTest {

    private static final OffsetDateTime AGORA = OffsetDateTime.of(2026, 9, 14, 10, 0, 0, 0, ZoneOffset.UTC);
    private static final ConteudoNotificacao CONTEUDO = new ConteudoNotificacao("Titulo", "Mensagem", null);

    @Autowired
    private NotificacaoJpaRepository repository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private EntityManager entityManager;

    private UUID usuarioId;

    @BeforeEach
    void criarUsuario() {
        usuarioId = novoUsuario();
    }

    private UUID novoUsuario() {
        return usuarioRepository
                .saveAndFlush(Usuario.criar("notificacao-" + UUID.randomUUID() + "@sep.test", "hash", Role.CLIENTE))
                .getId();
    }

    private static OrigemNotificacao origem(String id) {
        return new OrigemNotificacao(TipoNotificacao.DESEMBOLSO_PIX_CONCLUIDO, id);
    }

    private UUID salvar(Notificacao notificacao) {
        return repository
                .saveAndFlush(NotificacaoJpaEntity.de(notificacao))
                .paraDominio()
                .getId();
    }

    private Notificacao emailQueFalhou(UUID usuario, String origemId) {
        Notificacao email = Notificacao.registrarEmail(usuario, origem(origemId), CONTEUDO, AGORA);
        email.registrarFalha("MailSendException", AGORA.plusSeconds(1));
        return email;
    }

    @Test
    void tabelaNaoTemColunaDeDadoPessoal() {
        @SuppressWarnings("unchecked")
        List<String> colunas = entityManager
                .createNativeQuery(
                        "select column_name from information_schema.columns where table_name = 'notificacao'")
                .getResultList();

        assertThat(colunas)
                .containsExactlyInAnyOrder(
                        "id",
                        "usuario_id",
                        "tipo",
                        "origem_id",
                        "canal",
                        "situacao",
                        "situacao_atualizada_em",
                        "motivo_falha",
                        "titulo",
                        "mensagem",
                        "referencia_tipo",
                        "referencia_id",
                        "criada_em",
                        "lida_em",
                        "data_criacao",
                        "data_modificacao",
                        "criado_por",
                        "modificado_por");
    }

    @Test
    void idaEVolta_preservaOAgregado() {
        UUID contratoId = UUID.randomUUID();
        Notificacao original = Notificacao.disponibilizarInApp(
                usuarioId,
                origem("transferencia-1"),
                new ConteudoNotificacao("Titulo", "Mensagem", new Referencia(TipoReferencia.CONTRATO, contratoId)),
                AGORA);
        original.marcarLida(AGORA.plusMinutes(5));
        repository.saveAndFlush(NotificacaoJpaEntity.de(original));
        entityManager.clear();

        Notificacao lida = repository.findById(original.getId()).orElseThrow().paraDominio();

        assertThat(lida.getUsuarioId()).isEqualTo(usuarioId);
        assertThat(lida.getOrigem()).isEqualTo(original.getOrigem());
        assertThat(lida.getConteudo()).isEqualTo(original.getConteudo());
        assertThat(lida.getEntrega().canal()).isEqualTo(original.getEntrega().canal());
        assertThat(lida.getEntrega().situacao()).isEqualTo(SituacaoEntrega.DISPONIVEL);
        assertThat(lida.getEntrega().atualizadaEm()).isAtSameInstantAs(AGORA);
        assertThat(lida.getCriadaEm()).isAtSameInstantAs(AGORA);
        assertThat(lida.getLidaEm())
                .hasValueSatisfying(instante -> assertThat(instante).isAtSameInstantAs(AGORA.plusMinutes(5)));
    }

    @Test
    void mesmaOrigemParaOMesmoUsuario_naoTemDuasLinhas() {
        salvar(Notificacao.disponibilizarInApp(usuarioId, origem("transferencia-1"), CONTEUDO, AGORA));

        assertThatThrownBy(() ->
                        salvar(Notificacao.disponibilizarInApp(usuarioId, origem("transferencia-1"), CONTEUDO, AGORA)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_notificacao_origem");
    }

    @Test
    void tentativaQueFalhou_naoBloqueiaAsSeguintes() {
        salvar(emailQueFalhou(usuarioId, "bloqueio-1"));
        salvar(emailQueFalhou(usuarioId, "bloqueio-1"));

        UUID pendente = salvar(Notificacao.registrarEmail(usuarioId, origem("bloqueio-1"), CONTEUDO, AGORA));

        assertThat(repository.findById(pendente)).isPresent();
    }

    @Test
    void mesmaOrigemParaOutroUsuario_eOutraOrigemParaOMesmoUsuario_saoLegitimas() {
        UUID outroUsuario = novoUsuario();
        salvar(Notificacao.disponibilizarInApp(usuarioId, origem("transferencia-1"), CONTEUDO, AGORA));

        UUID outroDestinatario =
                salvar(Notificacao.disponibilizarInApp(outroUsuario, origem("transferencia-1"), CONTEUDO, AGORA));
        UUID outraOrigem =
                salvar(Notificacao.disponibilizarInApp(usuarioId, origem("transferencia-2"), CONTEUDO, AGORA));

        assertThat(repository.findAllById(List.of(outroDestinatario, outraOrigem)))
                .hasSize(2);
    }

    @Test
    void usuarioInexistente_violaAChaveEstrangeira() {
        assertThatThrownBy(() -> salvar(
                        Notificacao.disponibilizarInApp(UUID.randomUUID(), origem("transferencia-1"), CONTEUDO, AGORA)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_notificacao_usuario");
    }

    @Test
    void check_inAppSoAdmiteDisponivel() {
        UUID id = salvar(Notificacao.disponibilizarInApp(usuarioId, origem("t-1"), CONTEUDO, AGORA));

        assertViolaCheck(
                "update notificacao set situacao = 'PENDENTE' where id = :id", id, "chk_notificacao_canal_situacao");
    }

    @Test
    void check_smsNaoTemEntrega() {
        UUID id = salvar(Notificacao.registrarEmail(usuarioId, origem("t-1"), CONTEUDO, AGORA));

        assertViolaCheck("update notificacao set canal = 'SMS' where id = :id", id, "chk_notificacao_canal_situacao");
    }

    @Test
    void check_motivoSoQuandoFalhou() {
        UUID id = salvar(Notificacao.registrarEmail(usuarioId, origem("t-1"), CONTEUDO, AGORA));

        assertViolaCheck(
                "update notificacao set motivo_falha = 'x' where id = :id", id, "chk_notificacao_motivo_falha");
    }

    @Test
    void check_leituraSoEmInApp() {
        UUID id = salvar(Notificacao.registrarEmail(usuarioId, origem("t-1"), CONTEUDO, AGORA));

        assertViolaCheck(
                "update notificacao set lida_em = criada_em where id = :id", id, "chk_notificacao_leitura_in_app");
    }

    @Test
    void check_referenciaCompletaEPermitida() {
        Notificacao comReferencia = Notificacao.disponibilizarInApp(
                usuarioId,
                origem("t-1"),
                new ConteudoNotificacao(
                        "Titulo", "Mensagem", new Referencia(TipoReferencia.CONTRATO, UUID.randomUUID())),
                AGORA);
        UUID id = salvar(comReferencia);

        assertViolaCheck(
                "update notificacao set referencia_id = null where id = :id", id, "chk_notificacao_referencia");
    }

    @Test
    void check_tipoFechado() {
        UUID id = salvar(Notificacao.disponibilizarInApp(usuarioId, origem("t-1"), CONTEUDO, AGORA));

        assertViolaCheck("update notificacao set tipo = 'MARKETING' where id = :id", id, "chk_notificacao_tipo");
    }

    private void assertViolaCheck(String sql, UUID id, String constraint) {
        assertThatThrownBy(() -> {
                    entityManager.createNativeQuery(sql).setParameter("id", id).executeUpdate();
                    entityManager.flush();
                })
                .isInstanceOf(PersistenceException.class)
                .rootCause()
                .hasMessageContaining(constraint);
    }
}
