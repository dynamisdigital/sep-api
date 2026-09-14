package com.dynamis.sep_api.notificacao.infrastructure.persistence;

import com.dynamis.sep_api.notificacao.domain.vo.CanalNotificacao;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository JPA da tabela {@code notificacao}; so os adapters de persistencia o usam.
 *
 * <p>As consultas da central escrevem {@code IN_APP} como literal, e nao como parametro: os indices
 * {@code idx_notificacao_central} e {@code idx_notificacao_nao_lidas} (V61) sao parciais em
 * {@code canal = 'IN_APP'}, e o PostgreSQL so prova esse predicado quando o valor esta no texto do
 * SQL. Com o canal como parametro, o plano generico de um prepared statement faz varredura
 * sequencial da tabela inteira (medido com 200 mil linhas na Task 38.4).
 */
public interface NotificacaoJpaRepository extends JpaRepository<NotificacaoJpaEntity, UUID> {

    @Query("select n from NotificacaoJpaEntity n where n.usuarioId = :usuarioId"
            + " and n.canal = com.dynamis.sep_api.notificacao.domain.vo.CanalNotificacao.IN_APP")
    Page<NotificacaoJpaEntity> listarDaCentral(@Param("usuarioId") UUID usuarioId, Pageable pageable);

    @Query("select count(n) from NotificacaoJpaEntity n where n.usuarioId = :usuarioId"
            + " and n.canal = com.dynamis.sep_api.notificacao.domain.vo.CanalNotificacao.IN_APP"
            + " and n.lidaEm is null")
    long contarNaoLidasDaCentral(@Param("usuarioId") UUID usuarioId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<NotificacaoJpaEntity> findByIdAndUsuarioIdAndCanal(UUID id, UUID usuarioId, CanalNotificacao canal);
}
