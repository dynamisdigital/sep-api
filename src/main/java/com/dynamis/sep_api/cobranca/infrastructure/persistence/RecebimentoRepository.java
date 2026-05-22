package com.dynamis.sep_api.cobranca.infrastructure.persistence;

import com.dynamis.sep_api.cobranca.domain.model.Recebimento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecebimentoRepository extends JpaRepository<Recebimento, UUID> {

    Optional<Recebimento> findByIdempotencyKey(String idempotencyKey);

    List<Recebimento> findByParcela_IdOrderByDataRecebimentoAsc(UUID parcelaId);
}
