package com.dynamis.sep_api.credores.infrastructure.persistence;

import com.dynamis.sep_api.credores.domain.model.MatchingCredoraOperacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Persistencia da sugestao de matching credora-operacao (Sprint 30). Segue o padrao vigente do
 * modulo {@code credores}: repository Spring Data usado direto pelos use cases (refactor para
 * portas fica no fim da fase, mesma divida registrada na Sprint 24).
 */
@Repository
public interface MatchingCredoraOperacaoRepository extends JpaRepository<MatchingCredoraOperacao, UUID> {}
