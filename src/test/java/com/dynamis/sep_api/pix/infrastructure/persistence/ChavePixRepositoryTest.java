package com.dynamis.sep_api.pix.infrastructure.persistence;

import com.dynamis.sep_api.escrow.domain.model.ContaEscrow;
import com.dynamis.sep_api.escrow.domain.vo.StatusContaEscrow;
import com.dynamis.sep_api.escrow.infrastructure.persistence.ContaEscrowRepository;
import com.dynamis.sep_api.pix.domain.model.ChavePix;
import com.dynamis.sep_api.pix.domain.vo.StatusChavePix;
import com.dynamis.sep_api.pix.domain.vo.TipoChavePix;
import com.dynamis.sep_api.shared.audit.AuditorAwareImpl;
import com.dynamis.sep_api.shared.audit.JpaAuditingConfig;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, AuditorAwareImpl.class})
@ActiveProfiles("dev")
class ChavePixRepositoryTest {

    private static final OffsetDateTime BASE = OffsetDateTime.of(2026, 7, 14, 10, 0, 0, 0, ZoneOffset.UTC);
    private static final String HASH_A = "a".repeat(64);
    private static final String HASH_B = "b".repeat(64);

    @Autowired
    private ChavePixRepository repository;

    @Autowired
    private ContaEscrowRepository contaEscrowRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager txManager;

    private UUID contaId;

    @BeforeEach
    void criarConta() {
        contaId = contaEscrowRepository
                .saveAndFlush(ContaEscrow.criar("TESTE-CHAVE-PIX-" + UUID.randomUUID(), StatusContaEscrow.ATIVA))
                .getId();
    }

    private ChavePix chave(String hash, String idempotencyKey, OffsetDateTime criadaEm) {
        return ChavePix.cadastrar(
                contaId,
                TipoChavePix.EMAIL,
                hash,
                "us***om",
                "prov-" + idempotencyKey,
                idempotencyKey,
                UUID.randomUUID(),
                criadaEm);
    }

    @Test
    void tabelaNaoTemColunaDeValorBruto() {
        @SuppressWarnings("unchecked")
        List<String> colunas = entityManager
                .createNativeQuery("select column_name from information_schema.columns"
                        + " where table_name = 'chave_pix' order by column_name")
                .getResultList();

        assertThat(colunas)
                .containsExactlyInAnyOrder(
                        "id",
                        "conta_escrow_id",
                        "tipo",
                        "valor_hash",
                        "valor_mascarado",
                        "status",
                        "provider_key_id",
                        "idempotency_key",
                        "criada_por_usuario_id",
                        "removida_por_usuario_id",
                        "criada_em",
                        "removida_em",
                        "data_criacao",
                        "data_modificacao",
                        "criado_por",
                        "modificado_por");
    }

    @Test
    void mesmaIdempotencyKeyNaConta_naoDuplica() {
        repository.saveAndFlush(chave(HASH_A, "idem-1", BASE));

        assertThatThrownBy(() -> repository.saveAndFlush(chave(HASH_B, "idem-1", BASE.plusMinutes(1))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void mesmaChaveNormalizada_naoTemDuasLinhasAtivasNaConta() {
        repository.saveAndFlush(chave(HASH_A, "idem-1", BASE));

        assertThatThrownBy(() -> repository.saveAndFlush(chave(HASH_A, "idem-2", BASE.plusMinutes(1))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void reativacaoAposInativa_ePermitidaComNovaIdempotencyKey() {
        ChavePix primeira = chave(HASH_A, "idem-1", BASE);
        primeira.inativar(UUID.randomUUID(), BASE.plusMinutes(5));
        repository.saveAndFlush(primeira);

        ChavePix segunda = repository.saveAndFlush(chave(HASH_A, "idem-2", BASE.plusMinutes(10)));

        assertThat(segunda.getStatus()).isEqualTo(StatusChavePix.ATIVA);
        assertThat(repository.findAllByContaEscrowIdOrderByCriadaEmDesc(contaId))
                .hasSize(2);
    }

    @Test
    void listagem_incluiAtivaEInativaOrdenadaPorCriacaoDecrescente() {
        ChavePix antiga = chave(HASH_A, "idem-1", BASE);
        antiga.inativar(UUID.randomUUID(), BASE.plusMinutes(1));
        repository.saveAndFlush(antiga);
        repository.saveAndFlush(chave(HASH_B, "idem-2", BASE.plusMinutes(2)));

        List<ChavePix> chaves = repository.findAllByContaEscrowIdOrderByCriadaEmDesc(contaId);

        assertThat(chaves).extracting(ChavePix::getIdempotencyKey).containsExactly("idem-2", "idem-1");
        assertThat(chaves)
                .extracting(ChavePix::getStatus)
                .containsExactly(StatusChavePix.ATIVA, StatusChavePix.INATIVA);
    }

    @Test
    void consultasDeReplayEDuplicataAtiva_encontramPorEscopoDaConta() {
        repository.saveAndFlush(chave(HASH_A, "idem-1", BASE));

        assertThat(repository.findByContaEscrowIdAndIdempotencyKey(contaId, "idem-1"))
                .isPresent();
        assertThat(repository.findByContaEscrowIdAndIdempotencyKey(UUID.randomUUID(), "idem-1"))
                .isEmpty();
        assertThat(repository.findByContaEscrowIdAndTipoAndValorHashAndStatus(
                        contaId, TipoChavePix.EMAIL, HASH_A, StatusChavePix.ATIVA))
                .isPresent();
        assertThat(repository.findByContaEscrowIdAndTipoAndValorHashAndStatus(
                        contaId, TipoChavePix.EMAIL, HASH_B, StatusChavePix.ATIVA))
                .isEmpty();
    }

    @Test
    void checkDeCoerencia_impedeInativaSemRegistroDeRemocao() {
        ChavePix ativa = repository.saveAndFlush(chave(HASH_A, "idem-1", BASE));

        assertThatThrownBy(() -> {
                    entityManager
                            .createNativeQuery("update chave_pix set status = 'INATIVA' where id = :id")
                            .setParameter("id", ativa.getId())
                            .executeUpdate();
                    entityManager.flush();
                })
                .isInstanceOf(PersistenceException.class)
                .rootCause()
                .hasMessageContaining("chk_chave_pix_remocao_coerente");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void lockPessimista_serializaRemocoesConcorrentes() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        UUID chaveId = tx.execute(status -> {
            contaId = contaEscrowRepository
                    .save(ContaEscrow.criar("TESTE-LOCK-" + UUID.randomUUID(), StatusContaEscrow.ATIVA))
                    .getId();
            return repository.save(chave(HASH_A, "idem-lock", BASE)).getId();
        });

        CountDownLatch primeiraSegurouLock = new CountDownLatch(1);
        CountDownLatch podeCommitar = new CountDownLatch(1);
        AtomicReference<Boolean> primeiraMudou = new AtomicReference<>();
        AtomicReference<Boolean> segundaMudou = new AtomicReference<>();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            var primeira = executor.submit(() -> tx.executeWithoutResult(status -> {
                ChavePix bloqueada = repository
                        .findByIdAndContaEscrowIdForUpdate(chaveId, contaId)
                        .orElseThrow();
                primeiraSegurouLock.countDown();
                aguardar(podeCommitar);
                primeiraMudou.set(bloqueada.inativar(UUID.randomUUID(), BASE.plusMinutes(1)));
            }));
            var segunda = executor.submit(() -> {
                aguardar(primeiraSegurouLock);
                podeCommitar.countDown();
                tx.executeWithoutResult(status -> {
                    ChavePix aposLock = repository
                            .findByIdAndContaEscrowIdForUpdate(chaveId, contaId)
                            .orElseThrow();
                    segundaMudou.set(aposLock.inativar(UUID.randomUUID(), BASE.plusMinutes(2)));
                });
            });
            primeira.get(30, TimeUnit.SECONDS);
            segunda.get(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
            tx.executeWithoutResult(status -> {
                repository.deleteById(chaveId);
                contaEscrowRepository.deleteById(contaId);
            });
        }

        // O FOR UPDATE faz a segunda tx enxergar a inativacao commitada pela primeira:
        // apenas uma remocao e efetiva.
        assertThat(primeiraMudou.get()).isTrue();
        assertThat(segundaMudou.get()).isFalse();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void advisoryLock_serializaCadastrosConcorrentesPelaMesmaChave() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        long chaveDeTrava = 987654321L;

        CountDownLatch primeiraSegurouLock = new CountDownLatch(1);
        CountDownLatch podeCommitar = new CountDownLatch(1);
        AtomicReference<Boolean> primeiraCommitouAntesDaSegundaEntrar = new AtomicReference<>(false);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            var primeira = executor.submit(() -> tx.executeWithoutResult(status -> {
                repository.travarCadastroChave(chaveDeTrava);
                primeiraSegurouLock.countDown();
                aguardar(podeCommitar);
                // Ultima instrucao antes do commit: o advisory xact lock so solta no fim da tx.
                primeiraCommitouAntesDaSegundaEntrar.set(true);
            }));
            var segunda = executor.submit(() -> {
                aguardar(primeiraSegurouLock);
                podeCommitar.countDown();
                tx.executeWithoutResult(status -> {
                    repository.travarCadastroChave(chaveDeTrava);
                    // So entra aqui depois que a primeira tx terminou (lock transacional).
                    assertThat(primeiraCommitouAntesDaSegundaEntrar.get()).isTrue();
                });
            });
            primeira.get(30, TimeUnit.SECONDS);
            segunda.get(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    private static void aguardar(CountDownLatch latch) {
        try {
            if (!latch.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("timeout aguardando latch");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }
}
