package com.dynamis.sep_api.credores.infrastructure.persistence;

import com.dynamis.sep_api.credores.domain.model.MatchingCredoraOperacao;
import com.dynamis.sep_api.credores.domain.vo.StatusMatchingCredoraOperacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
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
}
