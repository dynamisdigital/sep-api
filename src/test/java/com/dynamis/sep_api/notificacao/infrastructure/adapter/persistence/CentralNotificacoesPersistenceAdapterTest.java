package com.dynamis.sep_api.notificacao.infrastructure.adapter.persistence;

import com.dynamis.sep_api.notificacao.application.port.out.CentralNotificacoesPort;
import com.dynamis.sep_api.notificacao.domain.model.Notificacao;
import com.dynamis.sep_api.notificacao.domain.vo.ConteudoNotificacao;
import com.dynamis.sep_api.notificacao.domain.vo.OrigemNotificacao;
import com.dynamis.sep_api.notificacao.domain.vo.SituacaoEntrega;
import com.dynamis.sep_api.notificacao.domain.vo.TipoNotificacao;
import com.dynamis.sep_api.notificacao.infrastructure.persistence.NotificacaoJpaEntity;
import com.dynamis.sep_api.notificacao.infrastructure.persistence.NotificacaoJpaRepository;
import com.dynamis.sep_api.shared.audit.AuditorAwareImpl;
import com.dynamis.sep_api.shared.audit.JpaAuditingConfig;
import com.dynamis.sep_api.usuarios.domain.model.Role;
import com.dynamis.sep_api.usuarios.domain.model.Usuario;
import com.dynamis.sep_api.usuarios.infrastructure.persistence.UsuarioRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Recorte da central contra o PostgreSQL: dono, canal, ordem e contagem (Sprint 38 Task 38.4). */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, AuditorAwareImpl.class, CentralNotificacoesPersistenceAdapter.class})
@ActiveProfiles("dev")
class CentralNotificacoesPersistenceAdapterTest {

    private static final OffsetDateTime BASE = OffsetDateTime.of(2026, 9, 14, 10, 0, 0, 0, ZoneOffset.UTC);

    @Autowired
    private CentralNotificacoesPort central;

    @Autowired
    private NotificacaoJpaRepository repository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private EntityManager entityManager;

    private UUID dono;
    private UUID outro;

    @BeforeEach
    void criarUsuarios() {
        dono = novoUsuario();
        outro = novoUsuario();
    }

    private UUID novoUsuario() {
        return usuarioRepository
                .saveAndFlush(Usuario.criar("central-" + UUID.randomUUID() + "@sep.test", "hash", Role.CLIENTE))
                .getId();
    }

    private Notificacao inApp(UUID usuario, OffsetDateTime criadaEm) {
        return salvar(Notificacao.disponibilizarInApp(usuario, origemNova(), conteudo(), criadaEm));
    }

    private Notificacao email(UUID usuario, OffsetDateTime criadaEm) {
        return salvar(Notificacao.registrarEmail(usuario, origemNova(), conteudo(), criadaEm));
    }

    private static OrigemNotificacao origemNova() {
        return new OrigemNotificacao(
                TipoNotificacao.DESEMBOLSO_PIX_CONCLUIDO, UUID.randomUUID().toString());
    }

    private static ConteudoNotificacao conteudo() {
        return new ConteudoNotificacao("Titulo", "Mensagem", null);
    }

    private Notificacao salvar(Notificacao notificacao) {
        repository.saveAndFlush(NotificacaoJpaEntity.de(notificacao));
        return notificacao;
    }

    private static List<UUID> ids(Page<Notificacao> pagina) {
        return pagina.getContent().stream().map(Notificacao::getId).toList();
    }

    @Test
    void listar_soInAppDoDono_maisRecentePrimeiro() {
        Notificacao antiga = inApp(dono, BASE);
        Notificacao recente = inApp(dono, BASE.plusMinutes(10));
        email(dono, BASE.plusMinutes(20));
        inApp(outro, BASE.plusMinutes(30));

        Page<Notificacao> pagina = central.listar(dono, 0, 20);

        assertThat(ids(pagina)).containsExactly(recente.getId(), antiga.getId());
        assertThat(pagina.getTotalElements()).isEqualTo(2);
    }

    @Test
    void listar_instantesIguais_desempataPorIdDecrescente() {
        List<UUID> esperados = Stream.of(inApp(dono, BASE), inApp(dono, BASE), inApp(dono, BASE))
                .map(Notificacao::getId)
                // Ordem de uuid no PostgreSQL e byte a byte, a mesma da representacao hexadecimal.
                .sorted(Comparator.comparing(UUID::toString).reversed())
                .toList();

        assertThat(ids(central.listar(dono, 0, 20))).containsExactlyElementsOf(esperados);
    }

    @Test
    void listar_paginaDepoisDaPrimeira_trazOProximoItemEOTotal() {
        inApp(dono, BASE.plusMinutes(2));
        Notificacao segunda = inApp(dono, BASE.plusMinutes(1));
        inApp(dono, BASE);

        Page<Notificacao> pagina = central.listar(dono, 1, 1);

        assertThat(ids(pagina)).containsExactly(segunda.getId());
        assertThat(pagina.getTotalElements()).isEqualTo(3);
    }

    @Test
    void contarNaoLidas_ignoraLidasEmailEOutroUsuario() {
        inApp(dono, BASE);
        Notificacao lida = Notificacao.disponibilizarInApp(dono, origemNova(), conteudo(), BASE);
        lida.marcarLida(BASE.plusMinutes(1));
        salvar(lida);
        email(dono, BASE);
        inApp(outro, BASE);

        assertThat(central.contarNaoLidas(dono)).isEqualTo(1);
        assertThat(central.contarNaoLidas(outro)).isEqualTo(1);
    }

    @Test
    void buscarParaAtualizar_naoEncontraNotificacaoDeOutroUsuarioNemEmail() {
        Notificacao alheia = inApp(outro, BASE);
        Notificacao emailDoDono = email(dono, BASE);
        Notificacao propria = inApp(dono, BASE);

        assertThat(central.buscarParaAtualizar(dono, alheia.getId())).isEmpty();
        assertThat(central.buscarParaAtualizar(dono, emailDoDono.getId())).isEmpty();
        assertThat(central.buscarParaAtualizar(dono, propria.getId()))
                .hasValueSatisfying(n -> assertThat(n.getId()).isEqualTo(propria.getId()));
    }

    @Test
    void registrarLeitura_persisteOInstanteDaLeitura() {
        Notificacao propria = inApp(dono, BASE);
        propria.marcarLida(BASE.plusMinutes(3));

        central.registrarLeitura(propria);
        entityManager.flush();
        entityManager.clear();

        Notificacao relida = repository.findById(propria.getId()).orElseThrow().paraDominio();
        assertThat(relida.getLidaEm()).hasValueSatisfying(i -> assertThat(i).isAtSameInstantAs(BASE.plusMinutes(3)));
        assertThat(relida.getEntrega().situacao()).isEqualTo(SituacaoEntrega.DISPONIVEL);
    }
}
