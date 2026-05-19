package com.dynamis.sep_api.credito.infrastructure.persistence;

import com.dynamis.sep_api.credito.domain.model.ConsentimentoOpenFinance;
import com.dynamis.sep_api.credito.domain.vo.StatusConsentimento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConsentimentoOpenFinanceRepository extends JpaRepository<ConsentimentoOpenFinance, UUID> {

    Optional<ConsentimentoOpenFinance> findByIdExternoCelcoin(String idExternoCelcoin);

    Optional<ConsentimentoOpenFinance> findFirstByPropostaIdAndStatusOrderByDataInicioDesc(
            UUID propostaId, StatusConsentimento status);

    Optional<ConsentimentoOpenFinance> findFirstByPropostaIdOrderByDataInicioDesc(UUID propostaId);
}
