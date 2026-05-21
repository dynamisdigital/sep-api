package com.dynamis.sep_api.contratos.application.usecase;

import com.dynamis.sep_api.contratos.application.usecase.command.RegistrarAceiteCommand;
import com.dynamis.sep_api.contratos.domain.event.ContratoAceitoEvent;
import com.dynamis.sep_api.contratos.domain.exception.ContratoEstadoInvalidoException;
import com.dynamis.sep_api.contratos.domain.exception.ContratoNaoEncontradoException;
import com.dynamis.sep_api.contratos.domain.exception.ContratoOwnershipException;
import com.dynamis.sep_api.contratos.domain.model.AceiteContrato;
import com.dynamis.sep_api.contratos.domain.model.Contrato;
import com.dynamis.sep_api.contratos.domain.model.VersaoContrato;
import com.dynamis.sep_api.contratos.infrastructure.persistence.AceiteContratoRepository;
import com.dynamis.sep_api.contratos.infrastructure.persistence.ContratoRepository;
import com.dynamis.sep_api.shared.exception.ConflitoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registra o aceite explicito do tomador sobre a versao vigente do contrato (Sprint 10 Task
 * 10.4).
 *
 * <p>Regras:
 *
 * <ul>
 *   <li>Contrato deve existir; senao {@link ContratoNaoEncontradoException} (404).
 *   <li>Tomador autenticado deve ser dono do contrato; senao {@link ContratoOwnershipException}
 *       (403).
 *   <li>Contrato deve estar em {@code AGUARDANDO_ACEITE}; senao
 *       {@link ContratoEstadoInvalidoException} (409).
 *   <li>Versao vigente nao pode ja ter aceite; senao {@link ConflitoException} (409,
 *       {@code CTR-409-002}). Caso teorico — {@code marcarAceito()} ja muda o estado, mas
 *       cobrimos por defesa em profundidade contra estado inconsistente.
 * </ul>
 *
 * <p>Usa {@link ContratoRepository#findByIdForUpdate} (PESSIMISTIC_WRITE) pra serializar aceite
 * vs cancelamento concorrentes. Step-up authentication e responsabilidade da borda web
 * ({@code @RequireStepUp}); use case nao re-valida.
 *
 * <p>Apos commit, publica {@link ContratoAceitoEvent} pra trilha auditavel (Task 10.7).
 */
@Service
public class RegistrarAceiteUseCase {

    public static final String CODIGO_VERSAO_JA_ACEITA = "CTR-409-002";

    private static final Logger log = LoggerFactory.getLogger(RegistrarAceiteUseCase.class);

    private final ContratoRepository contratoRepository;
    private final AceiteContratoRepository aceiteRepository;
    private final ApplicationEventPublisher eventPublisher;

    public RegistrarAceiteUseCase(
            ContratoRepository contratoRepository,
            AceiteContratoRepository aceiteRepository,
            ApplicationEventPublisher eventPublisher) {
        this.contratoRepository = contratoRepository;
        this.aceiteRepository = aceiteRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Contrato executar(RegistrarAceiteCommand command) {
        Contrato contrato = contratoRepository
                .findByIdForUpdate(command.contratoId())
                .orElseThrow(() -> ContratoNaoEncontradoException.porId(command.contratoId()));

        if (!contrato.getTomadorId().equals(command.tomadorAutenticadoId())) {
            throw new ContratoOwnershipException(contrato.getId());
        }

        if (!contrato.getStatus().permiteAceite()) {
            throw new ContratoEstadoInvalidoException("registrarAceite", contrato.getStatus());
        }

        VersaoContrato vigente = contrato.versaoVigente()
                .orElseThrow(() -> new IllegalStateException(
                        "Contrato " + contrato.getId() + " em AGUARDANDO_ACEITE sem versao vigente"));

        if (aceiteRepository.existsByVersaoId(vigente.getId())) {
            throw new ConflitoException(
                    CODIGO_VERSAO_JA_ACEITA, "Versao " + vigente.getNumero() + " do contrato ja foi aceita");
        }

        // Persist aceite primeiro: se falhar por unique constraint (race entre 2 PATCH /aceite),
        // o contrato ainda nao transicionou pra ACEITO. Lock pessimista no Contrato serializa o
        // caso real (2º caller espera o 1º commitar, le estado ACEITO e falha em permiteAceite),
        // mas o try/catch defende contra cenarios de borda + execucao fora do controller.
        AceiteContrato aceite = AceiteContrato.registrar(
                vigente, command.tomadorAutenticadoId(), command.ipOrigem(), command.userAgentOrigem());
        try {
            aceiteRepository.save(aceite);
            aceiteRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ConflitoException(
                    CODIGO_VERSAO_JA_ACEITA, "Versao " + vigente.getNumero() + " do contrato ja foi aceita");
        }

        contrato.marcarAceito();
        Contrato salvo = contratoRepository.save(contrato);

        log.info(
                "Contrato aceito: contratoId={} propostaId={} versao={} aceiteId={}",
                salvo.getId(),
                salvo.getPropostaId(),
                vigente.getNumero(),
                aceite.getId());

        eventPublisher.publishEvent(new ContratoAceitoEvent(
                salvo.getId(),
                salvo.getPropostaId(),
                salvo.getTomadorId(),
                vigente.getId(),
                vigente.getNumero(),
                vigente.getHashSha256(),
                aceite.getIpOrigem(),
                aceite.getUserAgentOrigem()));

        return salvo;
    }
}
