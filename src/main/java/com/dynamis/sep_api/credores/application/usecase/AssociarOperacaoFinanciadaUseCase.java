package com.dynamis.sep_api.credores.application.usecase;

import com.dynamis.sep_api.credores.application.dto.AssociarOperacaoFinanciadaCommand;
import com.dynamis.sep_api.credores.application.dto.OperacaoCarteiraView;
import com.dynamis.sep_api.credores.application.port.out.ConsultarContratoParaCarteiraCredoraPort;
import com.dynamis.sep_api.credores.application.service.OperacaoCarteiraEnricher;
import com.dynamis.sep_api.credores.domain.event.OperacaoFinanciadaAssociadaEvent;
import com.dynamis.sep_api.credores.domain.exception.ContratoNaoElegivelException;
import com.dynamis.sep_api.credores.domain.exception.CredoraNaoElegivelException;
import com.dynamis.sep_api.credores.domain.exception.EmpresaCredoraNaoEncontradaException;
import com.dynamis.sep_api.credores.domain.exception.OperacaoFinanciadaDuplicadaException;
import com.dynamis.sep_api.credores.domain.exception.OportunidadeNaoEncontradaException;
import com.dynamis.sep_api.credores.domain.model.EmpresaCredora;
import com.dynamis.sep_api.credores.domain.model.OperacaoFinanciada;
import com.dynamis.sep_api.credores.domain.model.OportunidadeInvestimento;
import com.dynamis.sep_api.credores.domain.vo.StatusCredora;
import com.dynamis.sep_api.credores.domain.vo.StatusElegibilidade;
import com.dynamis.sep_api.credores.infrastructure.persistence.EmpresaCredoraRepository;
import com.dynamis.sep_api.credores.infrastructure.persistence.OperacaoFinanciadaRepository;
import com.dynamis.sep_api.credores.infrastructure.persistence.OportunidadeInvestimentoRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Associacao operacional assistida de uma operacao financiada a carteira de uma credora (Sprint 17,
 * Task 17.4). Operacao administrativa explicita — interesse NAO vira carteira automaticamente; a
 * carteira nasce desta associacao. Sprint 17 nao move recurso financeiro real.
 *
 * <p>Pre-condicoes: credora existe e esta ATIVA + ELEGIVEL; contrato resolvido (da oportunidade ou
 * do comando) existe e e elegivel via porta; nao existe operacao duplicada para credora+contrato.
 */
@Service
public class AssociarOperacaoFinanciadaUseCase {

    private final EmpresaCredoraRepository empresaRepository;
    private final OportunidadeInvestimentoRepository oportunidadeRepository;
    private final OperacaoFinanciadaRepository operacaoRepository;
    private final ConsultarContratoParaCarteiraCredoraPort contratoPort;
    private final OperacaoCarteiraEnricher enricher;
    private final ApplicationEventPublisher eventPublisher;

    public AssociarOperacaoFinanciadaUseCase(
            EmpresaCredoraRepository empresaRepository,
            OportunidadeInvestimentoRepository oportunidadeRepository,
            OperacaoFinanciadaRepository operacaoRepository,
            ConsultarContratoParaCarteiraCredoraPort contratoPort,
            OperacaoCarteiraEnricher enricher,
            ApplicationEventPublisher eventPublisher) {
        this.empresaRepository = empresaRepository;
        this.oportunidadeRepository = oportunidadeRepository;
        this.operacaoRepository = operacaoRepository;
        this.contratoPort = contratoPort;
        this.enricher = enricher;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public OperacaoCarteiraView executar(AssociarOperacaoFinanciadaCommand cmd) {
        EmpresaCredora credora = empresaRepository
                .findById(cmd.empresaCredoraId())
                .orElseThrow(() -> new EmpresaCredoraNaoEncontradaException(cmd.empresaCredoraId()));
        if (credora.getStatus() != StatusCredora.ATIVA || credora.getElegibilidade() != StatusElegibilidade.ELEGIVEL) {
            throw new CredoraNaoElegivelException();
        }

        UUID oportunidadeId = cmd.oportunidadeId();
        UUID contratoId = cmd.contratoId();
        if (oportunidadeId != null) {
            OportunidadeInvestimento oportunidade = oportunidadeRepository
                    .findById(oportunidadeId)
                    .orElseThrow(() -> new OportunidadeNaoEncontradaException(oportunidadeId));
            if (oportunidade.getContratoId() != null) {
                contratoId = oportunidade.getContratoId();
            }
        }
        if (contratoId == null || contratoPort.consultarPorId(contratoId).isEmpty()) {
            throw new ContratoNaoElegivelException();
        }

        if (operacaoRepository.existsByEmpresaCredoraIdAndContratoId(credora.getId(), contratoId)) {
            throw new OperacaoFinanciadaDuplicadaException();
        }

        OperacaoFinanciada operacao =
                OperacaoFinanciada.associar(credora.getId(), contratoId, oportunidadeId, cmd.justificativa());
        operacaoRepository.save(operacao);
        eventPublisher.publishEvent(
                new OperacaoFinanciadaAssociadaEvent(operacao.getId(), credora.getId(), contratoId, cmd.atorId()));
        return enricher.enriquecer(operacao);
    }
}
