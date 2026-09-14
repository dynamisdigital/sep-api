package com.dynamis.sep_api.notificacao.infrastructure.persistence;

import com.dynamis.sep_api.notificacao.domain.vo.CanalNotificacao;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.util.UUID;

/** Repository JPA da tabela {@code notificacao}; so os adapters de persistencia o usam. */
public interface NotificacaoJpaRepository extends JpaRepository<NotificacaoJpaEntity, UUID> {

    Page<NotificacaoJpaEntity> findByUsuarioIdAndCanal(UUID usuarioId, CanalNotificacao canal, Pageable pageable);

    long countByUsuarioIdAndCanalAndLidaEmIsNull(UUID usuarioId, CanalNotificacao canal);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<NotificacaoJpaEntity> findByIdAndUsuarioIdAndCanal(UUID id, UUID usuarioId, CanalNotificacao canal);
}
