package com.dynamis.sep_api.onboarding.application.query;

import com.dynamis.sep_api.onboarding.domain.model.ConsultaCNPJ;
import com.dynamis.sep_api.onboarding.domain.model.KybEmpresa;
import com.dynamis.sep_api.onboarding.domain.model.SolicitacaoOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.TipoSolicitante;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.ConsultaCNPJRepository;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.KybEmpresaRepository;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.SolicitacaoOnboardingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementacao read-only de {@link ConsultarOnboardingParaCreditoQuery}. Le {@code
 * SolicitacaoOnboarding} e, no caso PJ, complementa com {@code dataAbertura} obtida da
 * {@code ConsultaCNPJ} (1:1 com {@code KybEmpresa}).
 */
@Service
public class ConsultarOnboardingParaCreditoService implements ConsultarOnboardingParaCreditoQuery {

    private final SolicitacaoOnboardingRepository solicitacaoRepository;
    private final KybEmpresaRepository kybRepository;
    private final ConsultaCNPJRepository consultaCnpjRepository;

    public ConsultarOnboardingParaCreditoService(
            SolicitacaoOnboardingRepository solicitacaoRepository,
            KybEmpresaRepository kybRepository,
            ConsultaCNPJRepository consultaCnpjRepository) {
        this.solicitacaoRepository = solicitacaoRepository;
        this.kybRepository = kybRepository;
        this.consultaCnpjRepository = consultaCnpjRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OnboardingResumoCredito> consultarPorId(UUID solicitacaoId) {
        return solicitacaoRepository.findById(solicitacaoId).map(this::mapear);
    }

    private OnboardingResumoCredito mapear(SolicitacaoOnboarding s) {
        LocalDate dataAbertura = s.getTipo() == TipoSolicitante.EMPRESA ? buscarDataAbertura(s.getId()) : null;
        return new OnboardingResumoCredito(
                s.getId(), s.getUsuarioId(), s.getTipo(), s.getStatus(), s.getDataNascimento(), dataAbertura);
    }

    private LocalDate buscarDataAbertura(UUID solicitacaoId) {
        return kybRepository
                .findBySolicitacaoId(solicitacaoId)
                .map(KybEmpresa::getId)
                .flatMap(consultaCnpjRepository::findByKybEmpresaId)
                .map(ConsultaCNPJ::getDataAbertura)
                .orElse(null);
    }
}
