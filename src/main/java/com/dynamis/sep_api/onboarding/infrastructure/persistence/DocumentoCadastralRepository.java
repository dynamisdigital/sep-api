package com.dynamis.sep_api.onboarding.infrastructure.persistence;

import com.dynamis.sep_api.onboarding.domain.model.DocumentoCadastral;
import com.dynamis.sep_api.onboarding.domain.vo.TipoDocumento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentoCadastralRepository extends JpaRepository<DocumentoCadastral, UUID> {

    boolean existsBySolicitacaoIdAndTipo(UUID solicitacaoId, TipoDocumento tipo);

    List<DocumentoCadastral> findBySolicitacaoId(UUID solicitacaoId);
}
