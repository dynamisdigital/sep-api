package com.dynamis.sep_api.credores.application.usecase;

import com.dynamis.sep_api.credores.domain.event.InteresseCredoraCanceladoEvent;
import com.dynamis.sep_api.credores.domain.exception.EmpresaCredoraNaoEncontradaException;
import com.dynamis.sep_api.credores.domain.exception.InteresseNaoEncontradoException;
import com.dynamis.sep_api.credores.domain.model.EmpresaCredora;
import com.dynamis.sep_api.credores.domain.model.InteresseCredora;
import com.dynamis.sep_api.credores.domain.vo.StatusInteresseCredora;
import com.dynamis.sep_api.credores.infrastructure.persistence.EmpresaCredoraRepository;
import com.dynamis.sep_api.credores.infrastructure.persistence.InteresseCredoraRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Cancela o interesse ativo da credora do usuario autenticado numa oportunidade (Sprint 17, Task
 * 17.4). Exige ownership (interesse pertence a credora do usuario) e interesse ativo existente.
 */
@Service
public class CancelarInteresseCredoraUseCase {

    private final EmpresaCredoraRepository empresaRepository;
    private final InteresseCredoraRepository interesseRepository;
    private final ApplicationEventPublisher eventPublisher;

    public CancelarInteresseCredoraUseCase(
            EmpresaCredoraRepository empresaRepository,
            InteresseCredoraRepository interesseRepository,
            ApplicationEventPublisher eventPublisher) {
        this.empresaRepository = empresaRepository;
        this.interesseRepository = interesseRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void executar(UUID usuarioId, UUID oportunidadeId) {
        EmpresaCredora credora = empresaRepository
                .findByUsuarioId(usuarioId)
                .orElseThrow(() -> EmpresaCredoraNaoEncontradaException.porUsuario(usuarioId));

        InteresseCredora interesse = interesseRepository
                .findByEmpresaCredoraIdAndOportunidadeIdAndStatus(
                        credora.getId(), oportunidadeId, StatusInteresseCredora.ATIVO)
                .orElseThrow(InteresseNaoEncontradoException::new);

        interesse.cancelar();
        interesseRepository.save(interesse);
        eventPublisher.publishEvent(
                new InteresseCredoraCanceladoEvent(interesse.getId(), credora.getId(), oportunidadeId, usuarioId));
    }
}
