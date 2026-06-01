package com.dynamis.sep_api.pix.application.service;

import com.dynamis.sep_api.pix.application.port.out.CobrancaDesembolsoQueryPort;
import com.dynamis.sep_api.pix.application.port.out.ContratoDesembolsoQueryPort;
import com.dynamis.sep_api.pix.application.port.out.EscrowDesembolsoQueryPort;
import com.dynamis.sep_api.pix.application.port.out.dto.AgendaDesembolsoView;
import com.dynamis.sep_api.pix.application.port.out.dto.ContratoDesembolsoView;
import com.dynamis.sep_api.pix.application.port.out.dto.EscrowDesembolsoView;
import com.dynamis.sep_api.pix.application.service.ResultadoElegibilidadeDesembolso.MotivoInelegibilidade;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Avalia, por ports de leitura cruzada, se um contrato pode receber desembolso Pix assistido (Sprint
 * 20 Task 20.1). Regras de elegibilidade, na ordem:
 *
 * <ol>
 *   <li>contrato existe;
 *   <li>contrato esta {@code ASSINADO};
 *   <li>existe agenda de cobranca ativa para o contrato;
 *   <li>existe wallet/conta escrow operacional para a proposta.
 * </ol>
 *
 * <p>A ausencia de transferencia Pix duplicada por contrato eh validada no use case de solicitacao
 * (Task 20.2), apos a coluna {@code contrato_id} existir em {@code pix_transferencia}.
 */
@Service
public class ValidadorElegibilidadeDesembolso {

    private final ContratoDesembolsoQueryPort contratoPort;
    private final CobrancaDesembolsoQueryPort cobrancaPort;
    private final EscrowDesembolsoQueryPort escrowPort;

    public ValidadorElegibilidadeDesembolso(
            ContratoDesembolsoQueryPort contratoPort,
            CobrancaDesembolsoQueryPort cobrancaPort,
            EscrowDesembolsoQueryPort escrowPort) {
        this.contratoPort = contratoPort;
        this.cobrancaPort = cobrancaPort;
        this.escrowPort = escrowPort;
    }

    public ResultadoElegibilidadeDesembolso validar(UUID contratoId) {
        Optional<ContratoDesembolsoView> contratoOpt = contratoPort.buscarPorContrato(contratoId);
        if (contratoOpt.isEmpty()) {
            return ResultadoElegibilidadeDesembolso.inelegivel(MotivoInelegibilidade.CONTRATO_NAO_ENCONTRADO);
        }
        ContratoDesembolsoView contrato = contratoOpt.get();
        if (!contrato.assinado()) {
            return ResultadoElegibilidadeDesembolso.inelegivel(MotivoInelegibilidade.CONTRATO_NAO_ASSINADO);
        }

        Optional<AgendaDesembolsoView> agendaOpt = cobrancaPort.buscarAgendaAtivaPorContrato(contratoId);
        if (agendaOpt.isEmpty()) {
            return ResultadoElegibilidadeDesembolso.inelegivel(MotivoInelegibilidade.AGENDA_INEXISTENTE);
        }

        Optional<EscrowDesembolsoView> escrowOpt = escrowPort.buscarPorProposta(contrato.propostaId());
        if (escrowOpt.isEmpty() || !escrowOpt.get().operacional()) {
            return ResultadoElegibilidadeDesembolso.inelegivel(MotivoInelegibilidade.ESCROW_INOPERANTE);
        }

        return ResultadoElegibilidadeDesembolso.elegivel(contrato, agendaOpt.get(), escrowOpt.get());
    }
}
