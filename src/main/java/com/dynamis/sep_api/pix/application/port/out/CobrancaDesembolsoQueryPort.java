package com.dynamis.sep_api.pix.application.port.out;

import com.dynamis.sep_api.pix.application.port.out.dto.AgendaDesembolsoView;

import java.util.Optional;
import java.util.UUID;

/**
 * Porta de saida para confirmar a existencia de agenda de cobranca ativa do contrato antes do
 * desembolso (Sprint 20 Task 20.1). Implementada por adapter em
 * {@code pix.infrastructure.adapter.cobranca}.
 */
public interface CobrancaDesembolsoQueryPort {

    Optional<AgendaDesembolsoView> buscarAgendaAtivaPorContrato(UUID contratoId);
}
