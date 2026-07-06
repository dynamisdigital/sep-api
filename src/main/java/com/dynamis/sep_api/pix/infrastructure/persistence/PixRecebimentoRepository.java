package com.dynamis.sep_api.pix.infrastructure.persistence;

import com.dynamis.sep_api.pix.domain.model.PixRecebimento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PixRecebimentoRepository extends JpaRepository<PixRecebimento, UUID> {

    Optional<PixRecebimento> findByEndToEndId(String endToEndId);

    /**
     * Recebimento mais recente correlacionado a uma referencia (Sprint 26, leitura owner-scoped do
     * status Pix da parcela). Buscado pela {@code referenciaId} da referencia atual, nunca pela
     * parcela diretamente, para nao casar uma referencia nova com recebimento de referencia antiga.
     */
    Optional<PixRecebimento> findFirstByReferenciaIdOrderByDataCriacaoDesc(UUID referenciaId);
}
