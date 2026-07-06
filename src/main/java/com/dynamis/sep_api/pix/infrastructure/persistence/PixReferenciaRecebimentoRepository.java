package com.dynamis.sep_api.pix.infrastructure.persistence;

import com.dynamis.sep_api.pix.domain.model.PixReferenciaRecebimento;
import com.dynamis.sep_api.pix.domain.vo.StatusPixReferenciaRecebimento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PixReferenciaRecebimentoRepository extends JpaRepository<PixReferenciaRecebimento, UUID> {

    /** Correlacao deterministica do webhook de recebimento (Sprint 21 Task 21.3). */
    Optional<PixReferenciaRecebimento> findByTxid(String txid);

    Optional<PixReferenciaRecebimento> findByProviderReferenciaId(String providerReferenciaId);

    /** Referencia ativa de uma parcela (idempotencia da geracao — no maximo uma ATIVA). */
    Optional<PixReferenciaRecebimento> findByParcelaIdAndStatus(UUID parcelaId, StatusPixReferenciaRecebimento status);

    /**
     * Referencia mais recente de uma parcela, sem filtro de status (Sprint 26, leitura owner-scoped
     * do status Pix da parcela do tomador).
     */
    Optional<PixReferenciaRecebimento> findFirstByParcelaIdOrderByDataCriacaoDesc(UUID parcelaId);
}
