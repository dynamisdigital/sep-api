package com.dynamis.sep_api.cobranca.infrastructure.persistence;

import com.dynamis.sep_api.cobranca.domain.model.Renegociacao;
import com.dynamis.sep_api.cobranca.domain.vo.StatusRenegociacao;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RenegociacaoRepository extends JpaRepository<Renegociacao, UUID> {

    /**
     * Lock pessimista pra evitar race entre aceitar/recusar/expirar concorrentes (Task 13.6 fix
     * code review). {@code SELECT FOR UPDATE} serializa as transicoes da renegociacao.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Renegociacao r where r.id = :id")
    Optional<Renegociacao> findByIdForUpdate(UUID id);

    Optional<Renegociacao> findByParcelaOriginalIdAndStatus(UUID parcelaOriginalId, StatusRenegociacao status);

    boolean existsByParcelaOriginalIdAndStatus(UUID parcelaOriginalId, StatusRenegociacao status);

    List<Renegociacao> findByStatusAndDataExpiracaoBefore(StatusRenegociacao status, OffsetDateTime corte);

    /**
     * Inclui {@code dataExpiracao == corte} — alinha com {@code Renegociacao.expirouEm} que trata
     * o instante exato como expirado (fix code review Task 13.6: {@code Before} sozinho deixaria
     * proposta com expiracao exata no momento da varredura pro proximo ciclo).
     */
    List<Renegociacao> findByStatusAndDataExpiracaoLessThanEqual(StatusRenegociacao status, OffsetDateTime corte);
}
