package com.dynamis.sep_api.onboarding.application.usecase;

import com.dynamis.sep_api.onboarding.domain.exception.KybNaoEncontradoException;
import com.dynamis.sep_api.onboarding.domain.exception.OnboardingNaoEncontradoException;
import com.dynamis.sep_api.onboarding.domain.model.KybEmpresa;
import com.dynamis.sep_api.onboarding.domain.model.RepresentanteLegal;
import com.dynamis.sep_api.onboarding.domain.model.SolicitacaoOnboarding;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.KybEmpresaRepository;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.RepresentanteLegalRepository;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.SolicitacaoOnboardingRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Consulta lista de representantes legais de uma solicitacao KYB PJ. Owner do solicitacao ou ADMIN.
 *
 * <p>Retorna o objeto de dominio; mapeamento pra DTO publico (CPF mascarado, status PLD publico)
 * cabe a camada web (Task 7.7).
 */
@Service
public class ConsultarRepresentantesLegaisUseCase {

    private final SolicitacaoOnboardingRepository solicitacaoRepository;
    private final KybEmpresaRepository kybRepository;
    private final RepresentanteLegalRepository representanteRepository;

    public ConsultarRepresentantesLegaisUseCase(
            SolicitacaoOnboardingRepository solicitacaoRepository,
            KybEmpresaRepository kybRepository,
            RepresentanteLegalRepository representanteRepository) {
        this.solicitacaoRepository = solicitacaoRepository;
        this.kybRepository = kybRepository;
        this.representanteRepository = representanteRepository;
    }

    @Transactional(readOnly = true)
    public List<RepresentanteLegal> executar(UUID solicitacaoId, UUID usuarioAutenticadoId, boolean isAdmin) {
        SolicitacaoOnboarding solicitacao = solicitacaoRepository
                .findById(solicitacaoId)
                .orElseThrow(() -> new OnboardingNaoEncontradoException(solicitacaoId));
        if (!isAdmin && !solicitacao.getUsuarioId().equals(usuarioAutenticadoId)) {
            throw new AccessDeniedException("Solicitacao nao pertence ao usuario autenticado");
        }
        KybEmpresa kyb = kybRepository
                .findBySolicitacaoId(solicitacaoId)
                .orElseThrow(() -> new KybNaoEncontradoException(solicitacaoId));
        return representanteRepository.findByKybEmpresaId(kyb.getId());
    }
}
