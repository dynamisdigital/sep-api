package com.dynamis.sep_api.pix.infrastructure.persistence;

import com.dynamis.sep_api.pix.domain.model.PixTransferencia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PixTransferenciaRepository extends JpaRepository<PixTransferencia, UUID> {

    Optional<PixTransferencia> findByIdempotencyKey(String idempotencyKey);
}
