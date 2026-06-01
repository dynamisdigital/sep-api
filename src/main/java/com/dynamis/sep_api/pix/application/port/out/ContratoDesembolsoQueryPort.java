package com.dynamis.sep_api.pix.application.port.out;

import com.dynamis.sep_api.pix.application.port.out.dto.ContratoDesembolsoView;

import java.util.Optional;
import java.util.UUID;

/**
 * Porta de saida que permite ao modulo {@code pix} ler o estado do contrato necessario para o
 * desembolso assistido (Sprint 20 Task 20.1) sem acoplar-se ao agregado {@code Contrato} de outro
 * modulo. Implementada por adapter em {@code pix.infrastructure.adapter.contratos}.
 */
public interface ContratoDesembolsoQueryPort {

    Optional<ContratoDesembolsoView> buscarPorContrato(UUID contratoId);
}
