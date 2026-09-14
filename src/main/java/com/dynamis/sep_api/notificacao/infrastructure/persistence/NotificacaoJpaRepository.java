package com.dynamis.sep_api.notificacao.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Repository JPA da tabela {@code notificacao}; so o adapter de persistencia o usa. */
public interface NotificacaoJpaRepository extends JpaRepository<NotificacaoJpaEntity, UUID> {}
