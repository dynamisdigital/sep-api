package com.dynamis.sep_api.credores.infrastructure.persistence;

import com.dynamis.sep_api.credores.domain.model.MatchingCredoraOperacao;
import com.dynamis.sep_api.credores.domain.vo.StatusMatchingCredoraOperacao;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistencia da sugestao de matching credora-operacao (Sprint 30). Segue o padrao vigente do
 * modulo {@code credores}: repository Spring Data usado direto pelos use cases (refactor para
 * portas fica no fim da fase, mesma divida registrada na Sprint 24).
 */
@Repository
public interface MatchingCredoraOperacaoRepository extends JpaRepository<MatchingCredoraOperacao, UUID> {

    /**
     * Matchings existentes (qualquer status) dos pares candidatos, em lote — a operacao pertence a
     * uma unica credora, entao o par e identificado pela operacao. Base da regra de nao duplicar
     * sugestao (REJEITADA tambem bloqueia, Task 30.1).
     */
    List<MatchingCredoraOperacao> findAllByOperacaoIdIn(Collection<UUID> operacaoIds);

    /** Listagem operacional deterministica: maior valor primeiro, empate pela sugestao mais antiga. */
    List<MatchingCredoraOperacao> findAllByStatusOrderByValorElegivelDescDataCriacaoAsc(
            StatusMatchingCredoraOperacao status);

    /**
     * Leitura com {@code SELECT FOR UPDATE} para serializar decisoes concorrentes sobre a mesma
     * sugestao (Sprint 30 Task 30.4): a segunda decisao bloqueia, re-le o status terminal e recebe
     * 409 — sem decisao dupla nem auditoria duplicada.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MatchingCredoraOperacao m where m.id = :id")
    Optional<MatchingCredoraOperacao> findByIdForUpdate(UUID id);
}
