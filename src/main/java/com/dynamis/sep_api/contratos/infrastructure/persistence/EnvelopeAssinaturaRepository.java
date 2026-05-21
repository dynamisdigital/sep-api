package com.dynamis.sep_api.contratos.infrastructure.persistence;

import com.dynamis.sep_api.contratos.domain.model.EnvelopeAssinatura;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface EnvelopeAssinaturaRepository extends JpaRepository<EnvelopeAssinatura, UUID> {

    Optional<EnvelopeAssinatura> findByContratoId(UUID contratoId);

    Optional<EnvelopeAssinatura> findByIdempotencyKey(String idempotencyKey);

    Optional<EnvelopeAssinatura> findByProviderAndIdEnvelopeExterno(String provider, String idEnvelopeExterno);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "select e from EnvelopeAssinatura e where e.provider = :provider and e.idEnvelopeExterno = :idEnvelopeExterno")
    Optional<EnvelopeAssinatura> findByProviderAndIdEnvelopeExternoForUpdate(String provider, String idEnvelopeExterno);
}
