package com.dynamis.sep_api.cobranca.infrastructure.persistence;

import com.dynamis.sep_api.cobranca.domain.model.Recebimento;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecebimentoRepository extends JpaRepository<Recebimento, UUID> {

    Optional<Recebimento> findByIdempotencyKey(String idempotencyKey);

    List<Recebimento> findByParcela_IdOrderByDataRecebimentoAsc(UUID parcelaId);

    /**
     * Listagem ordenada por data DESC com {@code parcela} carregada via fetch join (Sprint 12
     * Task 12.6 fix code review manual). Evita N+1 e leitura lazy fora da transacao quando o
     * mapper acessa {@code recebimento.getParcela()}.
     */
    @EntityGraph(attributePaths = "parcela")
    @Query("select r from Recebimento r order by r.dataRecebimento desc")
    List<Recebimento> findAllWithParcelaOrderByDataDesc();
}
