package com.dynamis.sep_api.onboarding.infrastructure.persistence;

import com.dynamis.sep_api.onboarding.domain.model.ResultadoVerificacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ResultadoVerificacaoRepository extends JpaRepository<ResultadoVerificacao, UUID> {

    Optional<ResultadoVerificacao> findBySolicitacaoId(UUID solicitacaoId);
}
