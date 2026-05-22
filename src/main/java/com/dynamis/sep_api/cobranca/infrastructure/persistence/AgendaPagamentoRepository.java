package com.dynamis.sep_api.cobranca.infrastructure.persistence;

import com.dynamis.sep_api.cobranca.domain.model.AgendaPagamento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AgendaPagamentoRepository extends JpaRepository<AgendaPagamento, UUID> {

    Optional<AgendaPagamento> findByContratoId(UUID contratoId);

    boolean existsByContratoId(UUID contratoId);
}
