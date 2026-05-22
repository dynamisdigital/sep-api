package com.dynamis.sep_api.cobranca.application.usecase;

import com.dynamis.sep_api.cobranca.domain.exception.AgendaPagamentoNaoEncontradaException;
import com.dynamis.sep_api.cobranca.domain.model.AgendaPagamento;
import com.dynamis.sep_api.cobranca.infrastructure.persistence.AgendaPagamentoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Consulta a agenda de um contrato (Sprint 12 Task 12.6 — endpoint GET /cobranca/contratos/{id}/agenda).
 * Lanca {@link AgendaPagamentoNaoEncontradaException} (HTTP 404) quando o contrato nao tem agenda
 * gerada (assinatura ainda nao processada).
 */
@Service
@Transactional(readOnly = true)
public class ConsultarAgendaPorContratoUseCase {

    private final AgendaPagamentoRepository agendaRepository;

    public ConsultarAgendaPorContratoUseCase(AgendaPagamentoRepository agendaRepository) {
        this.agendaRepository = agendaRepository;
    }

    public AgendaPagamento executar(UUID contratoId) {
        return agendaRepository
                .findByContratoId(contratoId)
                .orElseThrow(() -> AgendaPagamentoNaoEncontradaException.porContrato(contratoId));
    }
}
