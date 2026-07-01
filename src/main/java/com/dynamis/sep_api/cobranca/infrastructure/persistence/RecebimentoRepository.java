package com.dynamis.sep_api.cobranca.infrastructure.persistence;

import com.dynamis.sep_api.cobranca.domain.model.Recebimento;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecebimentoRepository extends JpaRepository<Recebimento, UUID> {

    Optional<Recebimento> findByIdempotencyKey(String idempotencyKey);

    List<Recebimento> findByParcela_IdOrderByDataRecebimentoAsc(UUID parcelaId);

    /** Historico owner-scoped do tomador (Sprint 23): recebimentos da parcela mais recentes primeiro. */
    List<Recebimento> findByParcela_IdOrderByDataRecebimentoDesc(UUID parcelaId);

    /**
     * Listagem ordenada por data DESC com {@code parcela} carregada via fetch join (Sprint 12
     * Task 12.6 fix code review manual). Evita N+1 e leitura lazy fora da transacao quando o
     * mapper acessa {@code recebimento.getParcela()}.
     */
    @EntityGraph(attributePaths = "parcela")
    @Query("select r from Recebimento r order by r.dataRecebimento desc")
    List<Recebimento> findAllWithParcelaOrderByDataDesc();

    /** Soma dos {@code valorRecebido} dentro do intervalo {@code [inicio, fim)} (Sprint 14 Task 14.5). */
    @Query("select coalesce(sum(r.valorRecebido), 0) from Recebimento r "
            + "where r.dataRecebimento >= :inicio and r.dataRecebimento < :fim")
    BigDecimal somarValorRecebidoNoIntervalo(@Param("inicio") OffsetDateTime inicio, @Param("fim") OffsetDateTime fim);
}
