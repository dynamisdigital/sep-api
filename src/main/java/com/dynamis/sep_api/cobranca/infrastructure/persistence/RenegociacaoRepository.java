package com.dynamis.sep_api.cobranca.infrastructure.persistence;

import com.dynamis.sep_api.cobranca.domain.model.Renegociacao;
import com.dynamis.sep_api.cobranca.domain.vo.StatusRenegociacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RenegociacaoRepository extends JpaRepository<Renegociacao, UUID> {

    Optional<Renegociacao> findByParcelaOriginalIdAndStatus(UUID parcelaOriginalId, StatusRenegociacao status);

    boolean existsByParcelaOriginalIdAndStatus(UUID parcelaOriginalId, StatusRenegociacao status);

    List<Renegociacao> findByStatusAndDataExpiracaoBefore(StatusRenegociacao status, OffsetDateTime corte);
}
