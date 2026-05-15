package com.dynamis.sep_api.onboarding.application.usecase;

import com.dynamis.sep_api.onboarding.domain.event.KybIniciadoEvent;
import com.dynamis.sep_api.onboarding.domain.exception.CnpjComOnboardingAtivoException;
import com.dynamis.sep_api.onboarding.domain.model.KybEmpresa;
import com.dynamis.sep_api.onboarding.domain.model.SolicitacaoOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.Cnpj;
import com.dynamis.sep_api.onboarding.domain.vo.PorteEmpresa;
import com.dynamis.sep_api.onboarding.domain.vo.StatusOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.TipoSocietario;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.KybEmpresaRepository;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.SolicitacaoOnboardingRepository;
import com.dynamis.sep_api.shared.exception.ValidacaoException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.UUID;

/**
 * Inicia uma solicitacao de onboarding KYB PJ para o usuario autenticado. Rejeita se ja houver
 * solicitacao ativa para o mesmo CNPJ. Cria 1:1 {@link SolicitacaoOnboarding} + {@link KybEmpresa}.
 */
@Service
public class IniciarOnboardingEmpresaUseCase {

    private static final String CODIGO_CNPJ_INVALIDO = "ONB-400-006";

    private final SolicitacaoOnboardingRepository solicitacaoRepository;
    private final KybEmpresaRepository kybRepository;
    private final ApplicationEventPublisher eventPublisher;

    public IniciarOnboardingEmpresaUseCase(
            SolicitacaoOnboardingRepository solicitacaoRepository,
            KybEmpresaRepository kybRepository,
            ApplicationEventPublisher eventPublisher) {
        this.solicitacaoRepository = solicitacaoRepository;
        this.kybRepository = kybRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public SolicitacaoOnboarding executar(
            UUID usuarioId,
            String cnpjBruto,
            String razaoSocial,
            String nomeFantasia,
            TipoSocietario tipoSocietario,
            PorteEmpresa porte) {
        Cnpj cnpj = parsearCnpj(cnpjBruto);
        validarObrigatorio(razaoSocial, "razaoSocial");

        boolean jaAtivo = solicitacaoRepository.existsByDocumentoAndStatusIn(
                cnpj.valor(),
                Arrays.stream(StatusOnboarding.values())
                        .filter(StatusOnboarding::isAtivo)
                        .toList());
        if (jaAtivo) {
            throw new CnpjComOnboardingAtivoException();
        }

        SolicitacaoOnboarding solicitacao = SolicitacaoOnboarding.criarEmpresa(usuarioId, cnpj.valor(), razaoSocial);
        solicitacaoRepository.save(solicitacao);

        KybEmpresa kyb = KybEmpresa.criar(solicitacao.getId(), cnpj, razaoSocial, nomeFantasia, tipoSocietario, porte);
        kybRepository.save(kyb);

        eventPublisher.publishEvent(new KybIniciadoEvent(solicitacao.getId(), usuarioId, cnpj.valor()));
        return solicitacao;
    }

    private static Cnpj parsearCnpj(String bruto) {
        if (bruto == null || bruto.isBlank()) {
            throw new ValidacaoException(CODIGO_CNPJ_INVALIDO, "CNPJ e obrigatorio");
        }
        try {
            return new Cnpj(bruto);
        } catch (IllegalArgumentException ex) {
            throw new ValidacaoException(CODIGO_CNPJ_INVALIDO, ex.getMessage());
        }
    }

    private static void validarObrigatorio(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new ValidacaoException("ONB-400-007", campo + " e obrigatorio");
        }
    }
}
