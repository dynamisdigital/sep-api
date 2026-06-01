package com.dynamis.sep_api.pix.infrastructure.adapter.cobranca;

import com.dynamis.sep_api.cobranca.infrastructure.persistence.AgendaPagamentoRepository;
import com.dynamis.sep_api.pix.application.port.out.CobrancaDesembolsoQueryPort;
import com.dynamis.sep_api.pix.application.port.out.dto.AgendaDesembolsoView;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Adapter que traduz {@link CobrancaDesembolsoQueryPort} para o repositorio de {@code cobranca}. Le
 * apenas a agenda ativa do contrato (a UNIQUE parcial WHERE ativa garante no maximo uma vigente).
 */
@Component
public class CobrancaDesembolsoQueryAdapter implements CobrancaDesembolsoQueryPort {

    private final AgendaPagamentoRepository agendaRepository;

    public CobrancaDesembolsoQueryAdapter(AgendaPagamentoRepository agendaRepository) {
        this.agendaRepository = agendaRepository;
    }

    @Override
    public Optional<AgendaDesembolsoView> buscarAgendaAtivaPorContrato(UUID contratoId) {
        return agendaRepository
                .findByContratoIdAndAtivaTrue(contratoId)
                .map(a -> new AgendaDesembolsoView(a.getContratoId(), a.getId(), a.getNumeroParcelas()));
    }
}
