package com.dynamis.sep_api.pix.infrastructure.adapter.cobranca;

import com.dynamis.sep_api.cobranca.application.port.out.ContratoCobrancaQueryPort;
import com.dynamis.sep_api.cobranca.infrastructure.persistence.ParcelaCobrancaRepository;
import com.dynamis.sep_api.pix.application.port.out.ParcelaTomadorQueryPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Adapter que resolve o tomador dono de uma parcela (Sprint 26 — Gate P2) reutilizando a mesma ponte
 * pix -> cobranca ja usada na Sprint 21: le a parcela, obtem o {@code contratoId} da agenda e delega o
 * {@code tomadorId} a {@link ContratoCobrancaQueryPort}. O dominio {@code pix} recebe apenas o UUID.
 */
@Component
public class ParcelaTomadorQueryAdapter implements ParcelaTomadorQueryPort {

    private final ParcelaCobrancaRepository parcelaRepository;
    private final ContratoCobrancaQueryPort contratoQueryPort;

    public ParcelaTomadorQueryAdapter(
            ParcelaCobrancaRepository parcelaRepository, ContratoCobrancaQueryPort contratoQueryPort) {
        this.parcelaRepository = parcelaRepository;
        this.contratoQueryPort = contratoQueryPort;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> tomadorIdDaParcela(UUID parcelaId) {
        // readOnly=true mantem o persistence context aberto para traversar a associacao LAZY da agenda.
        return parcelaRepository
                .findById(parcelaId)
                .map(parcela -> parcela.getAgenda().getContratoId())
                .flatMap(contratoQueryPort::tomadorIdDoContrato);
    }
}
