package com.dynamis.sep_api.credito.infrastructure.persistence;

import com.dynamis.sep_api.credito.domain.model.MovimentacaoOpenFinance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MovimentacaoOpenFinanceRepository extends JpaRepository<MovimentacaoOpenFinance, UUID> {

    Optional<MovimentacaoOpenFinance> findFirstByPropostaIdOrderByDataRecebimentoDesc(UUID propostaId);

    Optional<MovimentacaoOpenFinance> findFirstByConsentimentoIdOrderByDataRecebimentoDesc(UUID consentimentoId);
}
