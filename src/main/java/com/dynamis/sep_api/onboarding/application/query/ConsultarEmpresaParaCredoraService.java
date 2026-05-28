package com.dynamis.sep_api.onboarding.application.query;

import com.dynamis.sep_api.onboarding.domain.model.KybEmpresa;
import com.dynamis.sep_api.onboarding.domain.model.SolicitacaoOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.TipoSolicitante;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.KybEmpresaRepository;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.SolicitacaoOnboardingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Implementacao read-only de {@link ConsultarEmpresaParaCredoraQuery}. Le {@code
 * SolicitacaoOnboarding} e complementa, no caso PJ, com {@code cnpj} e {@code razaoSocial} do
 * {@code KybEmpresa} (1:1 com a solicitacao).
 */
@Service
public class ConsultarEmpresaParaCredoraService implements ConsultarEmpresaParaCredoraQuery {

    private final SolicitacaoOnboardingRepository solicitacaoRepository;
    private final KybEmpresaRepository kybRepository;

    public ConsultarEmpresaParaCredoraService(
            SolicitacaoOnboardingRepository solicitacaoRepository, KybEmpresaRepository kybRepository) {
        this.solicitacaoRepository = solicitacaoRepository;
        this.kybRepository = kybRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EmpresaParaCredoraResumo> consultarPorId(UUID solicitacaoId) {
        return solicitacaoRepository.findById(solicitacaoId).map(this::mapear);
    }

    private EmpresaParaCredoraResumo mapear(SolicitacaoOnboarding s) {
        String cnpj = null;
        String razaoSocial = null;
        if (s.getTipo() == TipoSolicitante.EMPRESA) {
            Optional<KybEmpresa> kyb = kybRepository.findBySolicitacaoId(s.getId());
            cnpj = kyb.map(KybEmpresa::getCnpj).orElse(null);
            razaoSocial = kyb.map(KybEmpresa::getRazaoSocial).orElse(null);
        }
        return new EmpresaParaCredoraResumo(s.getId(), s.getUsuarioId(), s.getTipo(), s.getStatus(), cnpj, razaoSocial);
    }
}
