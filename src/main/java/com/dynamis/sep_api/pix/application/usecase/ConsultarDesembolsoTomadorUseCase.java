package com.dynamis.sep_api.pix.application.usecase;

import com.dynamis.sep_api.pix.application.dto.PixDesembolsoTomadorResult;
import com.dynamis.sep_api.pix.application.mapper.StatusPixPublicoMapper;
import com.dynamis.sep_api.pix.application.port.out.ContratoDesembolsoQueryPort;
import com.dynamis.sep_api.pix.application.port.out.dto.ContratoDesembolsoView;
import com.dynamis.sep_api.pix.domain.exception.PixLeituraNaoEncontradaException;
import com.dynamis.sep_api.pix.domain.model.PixTransferencia;
import com.dynamis.sep_api.pix.infrastructure.persistence.PixTransferenciaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Consulta owner-scoped do status de desembolso Pix de um contrato do tomador (Sprint 26 — Gate P1).
 * Valida ownership do contrato ANTES de revelar a transferencia: contrato inexistente, contrato de
 * outro tomador e contrato sem desembolso Pix lancam a mesma {@link PixLeituraNaoEncontradaException}
 * (404 neutro), impedindo enumeracao. Leitura local, sem provider, mutacao ou step-up.
 */
@Service
@Transactional(readOnly = true)
public class ConsultarDesembolsoTomadorUseCase {

    private final ContratoDesembolsoQueryPort contratoPort;
    private final PixTransferenciaRepository transferenciaRepository;

    public ConsultarDesembolsoTomadorUseCase(
            ContratoDesembolsoQueryPort contratoPort, PixTransferenciaRepository transferenciaRepository) {
        this.contratoPort = contratoPort;
        this.transferenciaRepository = transferenciaRepository;
    }

    public PixDesembolsoTomadorResult executar(UUID contratoId, UUID clienteAutenticadoId) {
        ContratoDesembolsoView contrato =
                contratoPort.buscarPorContrato(contratoId).orElseThrow(PixLeituraNaoEncontradaException::new);
        if (!contrato.tomadorId().equals(clienteAutenticadoId)) {
            throw new PixLeituraNaoEncontradaException();
        }
        PixTransferencia transferencia = transferenciaRepository
                .findFirstByContratoIdOrderByDataCriacaoDesc(contratoId)
                .orElseThrow(PixLeituraNaoEncontradaException::new);
        return new PixDesembolsoTomadorResult(
                StatusPixPublicoMapper.mapear(transferencia.getStatus()),
                transferencia.getValor(),
                transferencia.getDataModificacao());
    }
}
