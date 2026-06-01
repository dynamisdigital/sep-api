package com.dynamis.sep_api.pix.application.service;

import com.dynamis.sep_api.pix.application.port.out.dto.RespostaTransferenciaPix;
import com.dynamis.sep_api.pix.application.port.out.dto.StatusTransferenciaPixProvider;
import com.dynamis.sep_api.pix.domain.event.PixTransferenciaConcluidaEvent;
import com.dynamis.sep_api.pix.domain.event.PixTransferenciaFalhouEvent;
import com.dynamis.sep_api.pix.domain.event.PixTransferenciaSolicitadaEvent;
import com.dynamis.sep_api.pix.domain.model.PixTransferencia;
import com.dynamis.sep_api.pix.domain.vo.StatusPixTransferencia;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * Centraliza as transicoes de estado de uma {@link PixTransferencia} a partir do status reportado
 * pelo provider, publicando os eventos de dominio correspondentes (Sprint 20 Task 20.3). Reusado
 * pela solicitacao, pela consulta de status e pelo webhook {@code STATUS_TRANSFERENCIA} (Task 20.4).
 */
@Service
public class SincronizadorStatusTransferencia {

    private final ApplicationEventPublisher eventPublisher;

    public SincronizadorStatusTransferencia(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /**
     * Aplica a resposta de uma <strong>solicitacao</strong> (transferencia em {@code CRIADA}). Sem
     * {@code externalId} valido ou status {@code REJEITADA}, marca falha; caso contrario avanca para
     * SOLICITADA/PROCESSANDO/CONCLUIDA conforme o provider.
     */
    public void aplicarRespostaSolicitacao(PixTransferencia transferencia, RespostaTransferenciaPix resposta) {
        String externalId = resposta.externalId();
        if (resposta.status() == StatusTransferenciaPixProvider.REJEITADA
                || externalId == null
                || externalId.isBlank()) {
            marcarFalhaTecnica(transferencia, "Provider rejeitou a transferencia ou nao retornou external id");
            return;
        }
        transferencia.marcarSolicitada(externalId);
        eventPublisher.publishEvent(new PixTransferenciaSolicitadaEvent(
                transferencia.getId(), transferencia.getContratoId(), externalId, transferencia.getValor()));
        if (resposta.status() == StatusTransferenciaPixProvider.PROCESSANDO) {
            transferencia.marcarProcessando();
        } else if (resposta.status() == StatusTransferenciaPixProvider.CONCLUIDA) {
            transferencia.marcarConcluida();
            publicarConcluida(transferencia);
        }
    }

    /** Marca falha tecnica (ex.: excecao do provider) e publica o evento de falha. */
    public void marcarFalhaTecnica(PixTransferencia transferencia, String motivo) {
        transferencia.marcarFalhou();
        eventPublisher.publishEvent(
                new PixTransferenciaFalhouEvent(transferencia.getId(), transferencia.getContratoId(), motivo));
    }

    /**
     * Sincroniza o status a partir de uma <strong>consulta/webhook</strong> de uma transferencia ja
     * solicitada. Avanca apenas para frente e eh idempotente: status terminal repetido ou status
     * que nao representa avanco nao altera a transferencia nem republica eventos.
     */
    public void sincronizar(PixTransferencia transferencia, StatusTransferenciaPixProvider status) {
        StatusPixTransferencia atual = transferencia.getStatus();
        boolean emAndamento = atual == StatusPixTransferencia.SOLICITADA || atual == StatusPixTransferencia.PROCESSANDO;
        if (!emAndamento) {
            return; // terminal ou ainda CRIADA: nada a sincronizar.
        }
        switch (status) {
            case PROCESSANDO -> {
                if (atual == StatusPixTransferencia.SOLICITADA) {
                    transferencia.marcarProcessando();
                }
            }
            case CONCLUIDA -> {
                transferencia.marcarConcluida();
                publicarConcluida(transferencia);
            }
            case REJEITADA -> marcarFalhaTecnica(transferencia, "Provider reportou rejeicao da transferencia");
            case PENDENTE -> {
                /* sem avanco */
            }
        }
    }

    private void publicarConcluida(PixTransferencia transferencia) {
        eventPublisher.publishEvent(new PixTransferenciaConcluidaEvent(
                transferencia.getId(), transferencia.getContratoId(), transferencia.getExternalId()));
    }
}
