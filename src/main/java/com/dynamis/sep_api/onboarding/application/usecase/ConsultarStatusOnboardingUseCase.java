package com.dynamis.sep_api.onboarding.application.usecase;

import com.dynamis.sep_api.onboarding.application.dto.StatusOnboardingView;
import com.dynamis.sep_api.onboarding.domain.exception.OnboardingNaoEncontradoException;
import com.dynamis.sep_api.onboarding.domain.model.ResultadoVerificacao;
import com.dynamis.sep_api.onboarding.domain.model.SolicitacaoOnboarding;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.DocumentoCadastralRepository;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.ResultadoVerificacaoRepository;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.SolicitacaoOnboardingRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Consulta status atual + documentos enviados + resultado de uma solicitacao. Cliente le apenas a
 * propria; admin le qualquer.
 */
@Service
public class ConsultarStatusOnboardingUseCase {

    private final SolicitacaoOnboardingRepository solicitacaoRepository;
    private final DocumentoCadastralRepository documentoRepository;
    private final ResultadoVerificacaoRepository resultadoRepository;

    public ConsultarStatusOnboardingUseCase(
            SolicitacaoOnboardingRepository solicitacaoRepository,
            DocumentoCadastralRepository documentoRepository,
            ResultadoVerificacaoRepository resultadoRepository) {
        this.solicitacaoRepository = solicitacaoRepository;
        this.documentoRepository = documentoRepository;
        this.resultadoRepository = resultadoRepository;
    }

    @Transactional(readOnly = true)
    public StatusOnboardingView executar(UUID solicitacaoId, UUID usuarioAutenticadoId, boolean isAdmin) {
        SolicitacaoOnboarding solicitacao = solicitacaoRepository
                .findById(solicitacaoId)
                .orElseThrow(() -> new OnboardingNaoEncontradoException(solicitacaoId));
        if (!isAdmin && !solicitacao.getUsuarioId().equals(usuarioAutenticadoId)) {
            throw new AccessDeniedException("Solicitacao nao pertence ao usuario autenticado");
        }

        var documentos = documentoRepository.findBySolicitacaoId(solicitacaoId).stream()
                .map(d -> new StatusOnboardingView.DocumentoEnviado(
                        d.getId(), d.getTipo(), d.getDataEnvio(), d.getSha256()))
                .toList();

        StatusOnboardingView.ResultadoView resultadoView = resultadoRepository
                .findBySolicitacaoId(solicitacaoId)
                .map(this::mapearResultado)
                .orElse(null);

        return new StatusOnboardingView(
                solicitacao.getId(),
                solicitacao.getStatus(),
                solicitacao.getDataCriacao(),
                solicitacao.getDataModificacao(),
                documentos,
                resultadoView);
    }

    private StatusOnboardingView.ResultadoView mapearResultado(ResultadoVerificacao r) {
        return new StatusOnboardingView.ResultadoView(r.getStatusFinal(), r.getMotivo(), r.getDataResultado());
    }
}
