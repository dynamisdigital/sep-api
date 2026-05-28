package com.dynamis.sep_api.credores.application.usecase;

import com.dynamis.sep_api.credores.application.dto.InteresseView;
import com.dynamis.sep_api.credores.domain.event.InteresseCredoraRegistradoEvent;
import com.dynamis.sep_api.credores.domain.exception.CredoraNaoElegivelException;
import com.dynamis.sep_api.credores.domain.exception.EmpresaCredoraNaoEncontradaException;
import com.dynamis.sep_api.credores.domain.exception.InteresseDuplicadoException;
import com.dynamis.sep_api.credores.domain.exception.OportunidadeIndisponivelException;
import com.dynamis.sep_api.credores.domain.exception.OportunidadeNaoEncontradaException;
import com.dynamis.sep_api.credores.domain.model.EmpresaCredora;
import com.dynamis.sep_api.credores.domain.model.InteresseCredora;
import com.dynamis.sep_api.credores.domain.model.OportunidadeInvestimento;
import com.dynamis.sep_api.credores.domain.vo.StatusCredora;
import com.dynamis.sep_api.credores.domain.vo.StatusElegibilidade;
import com.dynamis.sep_api.credores.domain.vo.StatusInteresseCredora;
import com.dynamis.sep_api.credores.infrastructure.persistence.EmpresaCredoraRepository;
import com.dynamis.sep_api.credores.infrastructure.persistence.InteresseCredoraRepository;
import com.dynamis.sep_api.credores.infrastructure.persistence.OportunidadeInvestimentoRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Registra a manifestacao de interesse de uma credora numa oportunidade (Sprint 17, Task 17.4).
 * Exige credora ATIVA + ELEGIVEL, oportunidade DISPONIVEL e ausencia de interesse ativo duplicado.
 */
@Service
public class RegistrarInteresseCredoraUseCase {

    private final EmpresaCredoraRepository empresaRepository;
    private final OportunidadeInvestimentoRepository oportunidadeRepository;
    private final InteresseCredoraRepository interesseRepository;
    private final ApplicationEventPublisher eventPublisher;

    public RegistrarInteresseCredoraUseCase(
            EmpresaCredoraRepository empresaRepository,
            OportunidadeInvestimentoRepository oportunidadeRepository,
            InteresseCredoraRepository interesseRepository,
            ApplicationEventPublisher eventPublisher) {
        this.empresaRepository = empresaRepository;
        this.oportunidadeRepository = oportunidadeRepository;
        this.interesseRepository = interesseRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public InteresseView executar(UUID usuarioId, UUID oportunidadeId) {
        EmpresaCredora credora = empresaRepository
                .findByUsuarioId(usuarioId)
                .orElseThrow(() -> EmpresaCredoraNaoEncontradaException.porUsuario(usuarioId));
        if (credora.getStatus() != StatusCredora.ATIVA || credora.getElegibilidade() != StatusElegibilidade.ELEGIVEL) {
            throw new CredoraNaoElegivelException();
        }

        OportunidadeInvestimento oportunidade = oportunidadeRepository
                .findById(oportunidadeId)
                .orElseThrow(() -> new OportunidadeNaoEncontradaException(oportunidadeId));
        if (!oportunidade.isDisponivel()) {
            throw new OportunidadeIndisponivelException();
        }

        if (interesseRepository.existsByEmpresaCredoraIdAndOportunidadeIdAndStatus(
                credora.getId(), oportunidadeId, StatusInteresseCredora.ATIVO)) {
            throw new InteresseDuplicadoException();
        }

        InteresseCredora interesse = InteresseCredora.registrar(credora.getId(), oportunidadeId);
        interesseRepository.save(interesse);
        eventPublisher.publishEvent(
                new InteresseCredoraRegistradoEvent(interesse.getId(), credora.getId(), oportunidadeId, usuarioId));
        return InteresseView.de(interesse);
    }
}
