package com.dynamis.sep_api.notificacao.infrastructure.adapter.persistence;

import com.dynamis.sep_api.notificacao.application.port.out.NotificacaoPort;
import com.dynamis.sep_api.notificacao.domain.model.Notificacao;
import com.dynamis.sep_api.notificacao.domain.vo.ConteudoNotificacao;
import com.dynamis.sep_api.notificacao.domain.vo.OrigemNotificacao;
import com.dynamis.sep_api.notificacao.domain.vo.TipoNotificacao;
import com.dynamis.sep_api.shared.audit.AuditorAwareImpl;
import com.dynamis.sep_api.shared.audit.JpaAuditingConfig;
import com.dynamis.sep_api.usuarios.domain.model.Role;
import com.dynamis.sep_api.usuarios.domain.model.Usuario;
import com.dynamis.sep_api.usuarios.infrastructure.persistence.UsuarioRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Deduplicacao por origem com transacoes reais (Sprint 38 Task 38.2). Sem transacao de teste: o
 * adapter comita em {@code REQUIRES_NEW}, entao usuario e notificacoes sao gravados de verdade e
 * apagados no {@code @AfterEach} — o {@code sep_dev} e compartilhado e residuo aqui quebraria os
 * {@code deleteAll()} de usuario de outras suites.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, AuditorAwareImpl.class, NotificacaoPersistenceAdapter.class})
@ActiveProfiles("dev")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class NotificacaoPersistenceAdapterTest {

    private static final OffsetDateTime AGORA = OffsetDateTime.of(2026, 9, 14, 10, 0, 0, 0, ZoneOffset.UTC);
    private static final ConteudoNotificacao CONTEUDO = new ConteudoNotificacao("Titulo", "Mensagem", null);
    private static final OrigemNotificacao BLOQUEIO =
            new OrigemNotificacao(TipoNotificacao.CONTA_BLOQUEADA, "2026-09-14T13:00:00Z");

    @Autowired
    private NotificacaoPort port;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate tx;
    private UUID usuarioId;

    @BeforeEach
    void criarUsuario() {
        tx = new TransactionTemplate(transactionManager);
        usuarioId = tx.execute(status -> usuarioRepository
                .save(Usuario.criar("notificacao-" + UUID.randomUUID() + "@sep.test", "hash", Role.CLIENTE))
                .getId());
    }

    @AfterEach
    void apagarResiduo() {
        tx.executeWithoutResult(status -> {
            entityManager
                    .createNativeQuery("delete from notificacao where usuario_id = :usuario")
                    .setParameter("usuario", usuarioId)
                    .executeUpdate();
            usuarioRepository.deleteById(usuarioId);
        });
    }

    private long linhasDoUsuario() {
        return tx.execute(status -> ((Number) entityManager
                        .createNativeQuery("select count(*) from notificacao where usuario_id = :usuario")
                        .setParameter("usuario", usuarioId)
                        .getSingleResult())
                .longValue());
    }

    private Notificacao emailDoBloqueio() {
        return Notificacao.registrarEmail(usuarioId, BLOQUEIO, CONTEUDO, AGORA);
    }

    @Test
    void registra_eComitaMesmoQueQuemChamaReverta() {
        tx.executeWithoutResult(status -> {
            assertThat(port.registrarSeInedita(emailDoBloqueio())).isTrue();
            status.setRollbackOnly();
        });

        assertThat(linhasDoUsuario()).isEqualTo(1);
    }

    @Test
    void replayDaMesmaOrigem_retornaFalseSemSegundaLinha() {
        assertThat(port.registrarSeInedita(emailDoBloqueio())).isTrue();

        assertThat(port.registrarSeInedita(emailDoBloqueio())).isFalse();

        assertThat(linhasDoUsuario()).isEqualTo(1);
    }

    @Test
    void tentativaQueFalhou_naoImpedeANova() {
        Notificacao falhou = emailDoBloqueio();
        falhou.registrarFalha("MailSendException", AGORA.plusSeconds(1));
        assertThat(port.registrarSeInedita(falhou)).isTrue();

        assertThat(port.registrarSeInedita(emailDoBloqueio())).isTrue();

        assertThat(linhasDoUsuario()).isEqualTo(2);
    }

    @Test
    void atualizarEntrega_gravaOResultadoDoEnvio() {
        Notificacao email = emailDoBloqueio();
        port.registrarSeInedita(email);
        email.registrarFalha("java.lang.IllegalStateException", AGORA.plusSeconds(5));

        port.atualizarEntrega(email);

        Object[] linha = tx.execute(status -> (Object[]) entityManager
                .createNativeQuery("select situacao, motivo_falha from notificacao where id = :id")
                .setParameter("id", email.getId())
                .getSingleResult());
        assertThat(linha).containsExactly("FALHOU", "java.lang.IllegalStateException");
    }

    @Test
    void atualizarEntrega_deNotificacaoNaoRegistrada_falha() {
        Notificacao naoRegistrada = emailDoBloqueio();
        naoRegistrada.registrarSimulacao(AGORA);

        assertThatThrownBy(() -> port.atualizarEntrega(naoRegistrada)).isInstanceOf(IllegalStateException.class);
        assertThat(linhasDoUsuario()).isZero();
    }

    @Test
    void violacaoQueNaoEAChaveDeOrigem_propaga() {
        Notificacao semUsuario = Notificacao.registrarEmail(UUID.randomUUID(), BLOQUEIO, CONTEUDO, AGORA);

        assertThatThrownBy(() -> port.registrarSeInedita(semUsuario))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_notificacao_usuario");
    }

    @Test
    void registrosConcorrentesDaMesmaOrigem_gravamUmaUnicaLinha() throws Exception {
        int concorrentes = 8;
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(concorrentes);
        List<Future<Boolean>> resultados = new ArrayList<>();
        try {
            for (int i = 0; i < concorrentes; i++) {
                Notificacao candidata = emailDoBloqueio();
                Callable<Boolean> registrar = () -> {
                    largada.await();
                    return port.registrarSeInedita(candidata);
                };
                resultados.add(executor.submit(registrar));
            }
            largada.countDown();

            long inseridas = 0;
            for (Future<Boolean> resultado : resultados) {
                if (resultado.get(30, TimeUnit.SECONDS)) {
                    inseridas++;
                }
            }

            assertThat(inseridas).isEqualTo(1);
            assertThat(linhasDoUsuario()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }
}
