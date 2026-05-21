package com.dynamis.sep_api.contratos.application.usecase;

import com.dynamis.sep_api.contratos.application.usecase.command.CancelarContratoCommand;
import com.dynamis.sep_api.contratos.domain.event.ContratoCanceladoEvent;
import com.dynamis.sep_api.contratos.domain.exception.ContratoEstadoInvalidoException;
import com.dynamis.sep_api.contratos.domain.exception.ContratoNaoEncontradoException;
import com.dynamis.sep_api.contratos.domain.model.Contrato;
import com.dynamis.sep_api.contratos.infrastructure.persistence.ContratoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cancela contrato pre-aceite por operacao do financeiro/admin (Sprint 10 Task 10.5).
 *
 * <p>Regras:
 *
 * <ul>
 *   <li>Contrato deve existir; senao {@link ContratoNaoEncontradoException} (404).
 *   <li>Contrato deve estar em {@code GERADO} ou {@code AGUARDANDO_ACEITE}; senao
 *       {@link ContratoEstadoInvalidoException} (409). Cancelar contrato ja aceito ou em
 *       assinatura requer endpoint/flow separado em sprint futura.
 *   <li>Justificativa minima/maxima ja validada pelo {@link CancelarContratoCommand}.
 * </ul>
 *
 * <p>Autorizacao (ROLE_FINANCEIRO ou ROLE_ADMIN) e step-up authentication sao validados na borda
 * web ({@code @PreAuthorize} + {@code @RequireStepUp} no controller da Task 10.6); o use case
 * confia no caller.
 *
 * <p>Usa {@link ContratoRepository#findByIdForUpdate} (PESSIMISTIC_WRITE) para serializar com
 * {@link RegistrarAceiteUseCase}: se aceite e cancelamento chegarem ao mesmo tempo, o lock
 * garante que o segundo caller leia o estado atualizado e falhe em {@code permiteCancelamento}.
 *
 * <p>Apos commit, publica {@link ContratoCanceladoEvent} para a trilha auditavel (Task 10.7).
 */
@Service
public class CancelarContratoUseCase {

    private static final Logger log = LoggerFactory.getLogger(CancelarContratoUseCase.class);

    private final ContratoRepository contratoRepository;
    private final ApplicationEventPublisher eventPublisher;

    public CancelarContratoUseCase(ContratoRepository contratoRepository, ApplicationEventPublisher eventPublisher) {
        this.contratoRepository = contratoRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Contrato executar(CancelarContratoCommand command) {
        Contrato contrato = contratoRepository
                .findByIdForUpdate(command.contratoId())
                .orElseThrow(() -> ContratoNaoEncontradoException.porId(command.contratoId()));

        if (!contrato.getStatus().permiteCancelamento()) {
            throw new ContratoEstadoInvalidoException("cancelar", contrato.getStatus());
        }

        contrato.cancelar();
        Contrato salvo = contratoRepository.save(contrato);

        log.info(
                "Contrato cancelado: contratoId={} propostaId={} canceladoPor={}",
                salvo.getId(),
                salvo.getPropostaId(),
                command.canceladoPorId());

        eventPublisher.publishEvent(new ContratoCanceladoEvent(
                salvo.getId(),
                salvo.getPropostaId(),
                salvo.getTomadorId(),
                command.canceladoPorId(),
                command.justificativa()));

        return salvo;
    }
}
