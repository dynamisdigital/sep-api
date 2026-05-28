package com.dynamis.sep_api.governanca.application.usecase;

import com.dynamis.sep_api.governanca.application.dto.AlterarParametroCommand;
import com.dynamis.sep_api.governanca.application.dto.ParametroOperacionalView;
import com.dynamis.sep_api.governanca.domain.event.ParametroOperacionalAlteradoEvent;
import com.dynamis.sep_api.governanca.domain.exception.ParametroOperacionalNaoEncontradoException;
import com.dynamis.sep_api.governanca.domain.model.ParametroOperacional;
import com.dynamis.sep_api.governanca.domain.model.VersaoParametroOperacional;
import com.dynamis.sep_api.governanca.infrastructure.persistence.ParametroOperacionalRepository;
import com.dynamis.sep_api.governanca.infrastructure.persistence.VersaoParametroOperacionalRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Altera o valor de um parametro operacional, validando o tipo, incrementando a versao e gravando
 * o historico com valor anterior/novo, ator e justificativa (Sprint 18). Autorizacao (ADMIN +
 * step-up) e aplicada no endpoint (Task 18.5). Publica evento para auditoria (Task 18.6).
 */
@Service
public class AlterarParametroOperacionalUseCase {

    private final ParametroOperacionalRepository parametroRepository;
    private final VersaoParametroOperacionalRepository versaoRepository;
    private final ApplicationEventPublisher eventPublisher;

    public AlterarParametroOperacionalUseCase(
            ParametroOperacionalRepository parametroRepository,
            VersaoParametroOperacionalRepository versaoRepository,
            ApplicationEventPublisher eventPublisher) {
        this.parametroRepository = parametroRepository;
        this.versaoRepository = versaoRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public ParametroOperacionalView executar(AlterarParametroCommand cmd) {
        ParametroOperacional parametro = parametroRepository
                .findByChave(cmd.chave())
                .orElseThrow(() -> new ParametroOperacionalNaoEncontradoException(cmd.chave()));

        String anterior = parametro.alterarValor(cmd.novoValor());
        parametroRepository.save(parametro);
        versaoRepository.save(VersaoParametroOperacional.registrar(
                parametro.getId(),
                parametro.getVersao(),
                anterior,
                cmd.novoValor(),
                cmd.atorId(),
                cmd.justificativa()));

        eventPublisher.publishEvent(new ParametroOperacionalAlteradoEvent(
                parametro.getId(),
                parametro.getChave(),
                parametro.getVersao(),
                anterior,
                cmd.novoValor(),
                cmd.atorId()));
        return ParametroOperacionalView.de(parametro);
    }
}
