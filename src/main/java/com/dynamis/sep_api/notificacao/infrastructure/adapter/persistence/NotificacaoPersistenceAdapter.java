package com.dynamis.sep_api.notificacao.infrastructure.adapter.persistence;

import com.dynamis.sep_api.notificacao.application.port.out.NotificacaoPort;
import com.dynamis.sep_api.notificacao.domain.model.Notificacao;
import com.dynamis.sep_api.notificacao.infrastructure.persistence.NotificacaoJpaEntity;
import com.dynamis.sep_api.notificacao.infrastructure.persistence.NotificacaoJpaRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Adapter de {@link NotificacaoPort} sobre JPA (Sprint 38, ADR 0021 §4).
 *
 * <p>A deduplicacao e da constraint {@code uq_notificacao_origem}, nao de um {@code exists} previo,
 * que nao resiste a corrida. A insercao roda em {@code REQUIRES_NEW} e a violacao e capturada
 * <b>fora</b> dela: capturar dentro deixaria a transacao marcada para rollback e o commit lancaria
 * {@code UnexpectedRollbackException} (mesmo desenho do {@code CriarItemFilaOperacionalService}).
 *
 * <p>So a violacao da chave de origem significa "ja notificado". FK para usuario inexistente ou
 * CHECK violado sao defeito de quem chama e propagam.
 */
@Component
public class NotificacaoPersistenceAdapter implements NotificacaoPort {

    static final String INDICE_CHAVE_DE_ORIGEM = "uq_notificacao_origem";

    private final NotificacaoJpaRepository repository;
    private final TransactionTemplate transacaoPropria;

    public NotificacaoPersistenceAdapter(
            NotificacaoJpaRepository repository, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.transacaoPropria = new TransactionTemplate(transactionManager);
        this.transacaoPropria.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public boolean registrarSeInedita(Notificacao notificacao) {
        try {
            transacaoPropria.executeWithoutResult(
                    status -> repository.saveAndFlush(NotificacaoJpaEntity.de(notificacao)));
            return true;
        } catch (DataIntegrityViolationException violacao) {
            if (violouChaveDeOrigem(violacao)) {
                return false;
            }
            throw violacao;
        }
    }

    @Override
    public void atualizarEntrega(Notificacao notificacao) {
        transacaoPropria.executeWithoutResult(status -> {
            NotificacaoJpaEntity entidade = repository
                    .findById(notificacao.getId())
                    .orElseThrow(() -> new IllegalStateException("notificacao nao registrada: " + notificacao.getId()));
            entidade.aplicarEstado(notificacao);
        });
    }

    private static boolean violouChaveDeOrigem(Throwable violacao) {
        for (Throwable causa = violacao; causa != null; causa = causa.getCause()) {
            if (causa instanceof ConstraintViolationException constraint) {
                return INDICE_CHAVE_DE_ORIGEM.equals(constraint.getConstraintName());
            }
        }
        return false;
    }
}
