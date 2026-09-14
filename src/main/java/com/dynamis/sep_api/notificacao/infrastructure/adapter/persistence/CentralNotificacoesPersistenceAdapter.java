package com.dynamis.sep_api.notificacao.infrastructure.adapter.persistence;

import com.dynamis.sep_api.notificacao.application.port.out.CentralNotificacoesPort;
import com.dynamis.sep_api.notificacao.domain.model.Notificacao;
import com.dynamis.sep_api.notificacao.domain.vo.CanalNotificacao;
import com.dynamis.sep_api.notificacao.infrastructure.persistence.NotificacaoJpaEntity;
import com.dynamis.sep_api.notificacao.infrastructure.persistence.NotificacaoJpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Adapter de {@link CentralNotificacoesPort} sobre JPA (Sprint 38 Task 38.4).
 *
 * <p>O dono e o canal {@code IN_APP} entram em <b>toda</b> consulta, inclusive na que antecede a
 * marcacao: o controle de acesso nao depende de quem chama lembrar de filtrar. Ao contrario do
 * {@link NotificacaoPersistenceAdapter}, aqui nada abre transacao propria — a central roda na
 * transacao do caso de uso.
 */
@Component
public class CentralNotificacoesPersistenceAdapter implements CentralNotificacoesPort {

    /** Mais recente primeiro; o id (UUID v6, ordenado no tempo) desempata instantes iguais. */
    private static final Sort ORDEM_DA_CENTRAL = Sort.by(Sort.Order.desc("criadaEm"), Sort.Order.desc("id"));

    private final NotificacaoJpaRepository repository;

    public CentralNotificacoesPersistenceAdapter(NotificacaoJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Page<Notificacao> listar(UUID usuarioId, int pagina, int tamanho) {
        return repository
                .findByUsuarioIdAndCanal(
                        usuarioId, CanalNotificacao.IN_APP, PageRequest.of(pagina, tamanho, ORDEM_DA_CENTRAL))
                .map(NotificacaoJpaEntity::paraDominio);
    }

    @Override
    public long contarNaoLidas(UUID usuarioId) {
        return repository.countByUsuarioIdAndCanalAndLidaEmIsNull(usuarioId, CanalNotificacao.IN_APP);
    }

    @Override
    public Optional<Notificacao> buscarParaAtualizar(UUID usuarioId, UUID notificacaoId) {
        return repository
                .findByIdAndUsuarioIdAndCanal(notificacaoId, usuarioId, CanalNotificacao.IN_APP)
                .map(NotificacaoJpaEntity::paraDominio);
    }

    @Override
    public void registrarLeitura(Notificacao notificacao) {
        NotificacaoJpaEntity entidade = repository
                .findById(notificacao.getId())
                .orElseThrow(() -> new IllegalStateException("notificacao nao registrada: " + notificacao.getId()));
        entidade.aplicarLeitura(notificacao);
        repository.save(entidade);
    }
}
