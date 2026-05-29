package com.dynamis.sep_api.pix.infrastructure.persistence;

import com.dynamis.sep_api.pix.domain.model.PixRecebimento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PixRecebimentoRepository extends JpaRepository<PixRecebimento, UUID> {

    Optional<PixRecebimento> findByEndToEndId(String endToEndId);
}
