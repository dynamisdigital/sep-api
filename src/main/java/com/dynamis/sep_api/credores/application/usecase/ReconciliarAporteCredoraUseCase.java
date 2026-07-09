package com.dynamis.sep_api.credores.application.usecase;

import com.dynamis.sep_api.credores.application.dto.AporteCredoraView;
import com.dynamis.sep_api.credores.application.dto.ReconciliarAporteCredoraCommand;
import com.dynamis.sep_api.credores.application.port.out.ReconciliarAporteEscrowPort;
import com.dynamis.sep_api.credores.domain.event.AporteCredoraFalhouEvent;
import com.dynamis.sep_api.credores.domain.event.AporteCredoraLiquidadoEvent;
import com.dynamis.sep_api.credores.domain.exception.AporteNaoEncontradoException;
import com.dynamis.sep_api.credores.domain.exception.AporteReconciliacaoConflitanteException;
import com.dynamis.sep_api.credores.domain.model.AporteCredora;
import com.dynamis.sep_api.credores.domain.vo.StatusAporteCredora;
import com.dynamis.sep_api.credores.infrastructure.persistence.AporteCredoraRepository;
import com.dynamis.sep_api.shared.exception.ValidacaoException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reconciliacao fake do status do aporte (Sprint 29 Task 29.5). Sem endpoint REST nesta fase: o
 * escrow e local (nenhum provider externo emite webhook), entao o "callback do provider fake" e uma
 * chamada direta a este use case — testes/smoke (Task 29.7) o acionam; a Fase 5 pluga o webhook
 * real Celcoin/BaaS aqui. Decisao alinhada aos steps ("handler/use case interno testavel").
 *
 * <p>Semantica:
 *
 * <ul>
 *   <li>{@code EM_PROCESSAMENTO -> LIQUIDADO}: liquida a movimentacao no escrow (credita wallet),
 *       transiciona o aporte e publica {@link AporteCredoraLiquidadoEvent} (auditoria terminal).
 *   <li>{@code EM_PROCESSAMENTO -> FALHOU}: marca falha no escrow (sem credito), guarda motivo
 *       sanitizado e publica {@link AporteCredoraFalhouEvent}.
 *   <li><strong>Replay identico</strong> (mesmo resultado em estado terminal): retorna o aporte sem
 *       alterar nada e sem republicar auditoria.
 *   <li><strong>Conflito</strong> (resultado divergente apos terminal): rejeita com 409 sem alterar
 *       estado — rejeicao explicita em vez de descarte silencioso, pois a chamada e direta (nao ha
 *       fila de eventos como no webhook Pix).
 * </ul>
 */
@Service
public class ReconciliarAporteCredoraUseCase {

    private static final int MOTIVO_MAX = 255;

    private final AporteCredoraRepository aporteRepository;
    private final ReconciliarAporteEscrowPort escrowPort;
    private final ApplicationEventPublisher eventPublisher;

    public ReconciliarAporteCredoraUseCase(
            AporteCredoraRepository aporteRepository,
            ReconciliarAporteEscrowPort escrowPort,
            ApplicationEventPublisher eventPublisher) {
        this.aporteRepository = aporteRepository;
        this.escrowPort = escrowPort;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public AporteCredoraView executar(ReconciliarAporteCredoraCommand cmd) {
        validarComando(cmd);

        AporteCredora aporte = aporteRepository
                .findByReferenciaEscrow(cmd.referenciaEscrow())
                .orElseThrow(AporteNaoEncontradoException::new);

        return switch (cmd.resultado()) {
            case LIQUIDADO -> liquidar(aporte);
            case FALHOU -> falhar(aporte, cmd.motivoSanitizado());
            default -> throw new IllegalStateException("resultado nao terminal apos validacao");
        };
    }

    private AporteCredoraView liquidar(AporteCredora aporte) {
        if (aporte.getStatus() == StatusAporteCredora.LIQUIDADO) {
            return AporteCredoraView.de(aporte);
        }
        exigirReconciliavel(aporte);
        escrowPort.liquidar(aporte.getReferenciaEscrow());
        aporte.marcarLiquidado();
        eventPublisher.publishEvent(new AporteCredoraLiquidadoEvent(
                aporte.getId(), aporte.getOperacaoId(), aporte.getEmpresaCredoraId(), aporte.getValor()));
        return AporteCredoraView.de(aporte);
    }

    private AporteCredoraView falhar(AporteCredora aporte, String motivoSanitizado) {
        if (aporte.getStatus() == StatusAporteCredora.FALHOU) {
            return AporteCredoraView.de(aporte);
        }
        exigirReconciliavel(aporte);
        escrowPort.falhar(aporte.getReferenciaEscrow());
        aporte.marcarFalhou(motivoSanitizado);
        eventPublisher.publishEvent(new AporteCredoraFalhouEvent(
                aporte.getId(), aporte.getOperacaoId(), aporte.getEmpresaCredoraId(), motivoSanitizado));
        return AporteCredoraView.de(aporte);
    }

    /** Resultado divergente sobre estado terminal e conflito explicito (409), sem alterar estado. */
    private void exigirReconciliavel(AporteCredora aporte) {
        if (aporte.getStatus() != StatusAporteCredora.EM_PROCESSAMENTO) {
            throw new AporteReconciliacaoConflitanteException();
        }
    }

    private void validarComando(ReconciliarAporteCredoraCommand cmd) {
        if (cmd.referenciaEscrow() == null || cmd.referenciaEscrow().isBlank()) {
            throw new ValidacaoException("CRD-400-007", "referenciaEscrow obrigatoria.");
        }
        if (cmd.resultado() != StatusAporteCredora.LIQUIDADO && cmd.resultado() != StatusAporteCredora.FALHOU) {
            throw new ValidacaoException("CRD-400-008", "resultado deve ser terminal (LIQUIDADO ou FALHOU).");
        }
        if (cmd.resultado() == StatusAporteCredora.FALHOU) {
            if (cmd.motivoSanitizado() == null || cmd.motivoSanitizado().isBlank()) {
                throw new ValidacaoException("CRD-400-009", "motivo sanitizado obrigatorio na falha.");
            }
            if (cmd.motivoSanitizado().length() > MOTIVO_MAX) {
                throw new ValidacaoException(
                        "CRD-400-010", "motivo sanitizado nao pode exceder " + MOTIVO_MAX + " caracteres.");
            }
        }
    }
}
