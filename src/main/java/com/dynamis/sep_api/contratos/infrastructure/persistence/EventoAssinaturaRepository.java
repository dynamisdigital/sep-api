package com.dynamis.sep_api.contratos.infrastructure.persistence;

import com.dynamis.sep_api.contratos.domain.model.EventoAssinatura;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EventoAssinaturaRepository extends JpaRepository<EventoAssinatura, UUID> {

    boolean existsByEnvelopeIdAndIdEventoExterno(UUID envelopeId, String idEventoExterno);
}
