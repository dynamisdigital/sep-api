package com.dynamis.sep_api.pix.application.usecase;

import com.dynamis.sep_api.pix.application.dto.ConsultarStatusDesembolsoPixCommand;
import com.dynamis.sep_api.pix.application.dto.StatusDesembolsoPixResult;
import com.dynamis.sep_api.pix.application.port.out.PixProvider;
import com.dynamis.sep_api.pix.application.port.out.dto.RespostaTransferenciaPix;
import com.dynamis.sep_api.pix.application.port.out.exception.PixProviderException;
import com.dynamis.sep_api.pix.application.service.SincronizadorStatusTransferencia;
import com.dynamis.sep_api.pix.domain.model.PixTransferencia;
import com.dynamis.sep_api.pix.domain.vo.StatusPixTransferencia;
import com.dynamis.sep_api.pix.infrastructure.persistence.PixTransferenciaRepository;
import com.dynamis.sep_api.shared.exception.RecursoNaoEncontradoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consulta o status de um desembolso Pix (Sprint 20 Task 20.3). Quando a transferencia ainda nao
 * esta terminal e tem {@code externalId}, consulta o {@link PixProvider} e sincroniza o status
 * idempotentemente via {@link SincronizadorStatusTransferencia}.
 *
 * <p>Leitura resiliente: se o provider falhar, devolve o status local atual em vez de propagar erro
 * — a consulta de status nunca deve derrubar por indisponibilidade do provider.
 */
@Service
public class ConsultarStatusDesembolsoPixUseCase {

    private static final Logger log = LoggerFactory.getLogger(ConsultarStatusDesembolsoPixUseCase.class);

    private final PixTransferenciaRepository transferenciaRepository;
    private final PixProvider pixProvider;
    private final SincronizadorStatusTransferencia sincronizador;

    public ConsultarStatusDesembolsoPixUseCase(
            PixTransferenciaRepository transferenciaRepository,
            PixProvider pixProvider,
            SincronizadorStatusTransferencia sincronizador) {
        this.transferenciaRepository = transferenciaRepository;
        this.pixProvider = pixProvider;
        this.sincronizador = sincronizador;
    }

    @Transactional
    public StatusDesembolsoPixResult executar(ConsultarStatusDesembolsoPixCommand cmd) {
        PixTransferencia transferencia = transferenciaRepository
                .findById(cmd.transferenciaId())
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "PIX-404-TRANSFERENCIA", "Transferencia Pix nao encontrada: " + cmd.transferenciaId()));

        if (deveConsultarProvider(transferencia)) {
            sincronizarComProvider(transferencia, cmd.correlationId());
        }
        return resultado(transferencia);
    }

    private boolean deveConsultarProvider(PixTransferencia transferencia) {
        StatusPixTransferencia status = transferencia.getStatus();
        boolean terminal = status == StatusPixTransferencia.CONCLUIDA
                || status == StatusPixTransferencia.FALHOU
                || status == StatusPixTransferencia.CANCELADA;
        boolean temExternalId = transferencia.getExternalId() != null
                && !transferencia.getExternalId().isBlank();
        return !terminal && temExternalId;
    }

    private void sincronizarComProvider(PixTransferencia transferencia, String correlationId) {
        try {
            RespostaTransferenciaPix resposta =
                    pixProvider.consultarTransferencia(transferencia.getExternalId(), correlationId);
            sincronizador.sincronizar(transferencia, resposta.status());
            transferenciaRepository.save(transferencia);
        } catch (PixProviderException ex) {
            log.warn(
                    "Consulta de status falhou no provider para transferencia={}; devolvendo status local. {}",
                    transferencia.getId(),
                    ex.getMessage());
        }
    }

    private StatusDesembolsoPixResult resultado(PixTransferencia t) {
        return new StatusDesembolsoPixResult(
                t.getId(), t.getContratoId(), t.getStatus(), t.getValor(), t.getChaveDestinoMascara());
    }
}
