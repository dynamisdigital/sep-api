package com.dynamis.sep_api.onboarding.application.usecase;

import com.dynamis.sep_api.onboarding.application.port.out.KycProvider;
import com.dynamis.sep_api.onboarding.application.port.out.dto.RequisicaoVerificacaoKyc;
import com.dynamis.sep_api.onboarding.application.port.out.dto.RespostaInicioVerificacao;
import com.dynamis.sep_api.onboarding.domain.event.VerificacaoKycDisparadaEvent;
import com.dynamis.sep_api.onboarding.domain.exception.OnboardingNaoEncontradoException;
import com.dynamis.sep_api.onboarding.domain.model.DocumentoCadastral;
import com.dynamis.sep_api.onboarding.domain.model.SolicitacaoOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.TipoDocumento;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.DocumentoCadastralRepository;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.SolicitacaoOnboardingRepository;
import com.dynamis.sep_api.shared.exception.ValidacaoException;
import com.dynamis.sep_api.shared.integration.IdempotencyKeyInterceptor;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Dispara a verificacao KYC no provider externo. Exige no minimo 1 documento de identidade (RG,
 * CNH ou PASSAPORTE) e 1 SELFIE.
 *
 * <p>Idempotency-Key deterministica baseada em {@code solicitacaoId + ":" + revisaoDocumentos},
 * preserva idempotencia mesmo em retries do caller ou da rede.
 */
@Service
public class IniciarVerificacaoKycUseCase {

    private static final Set<TipoDocumento> IDENTIDADE =
            Set.of(TipoDocumento.RG, TipoDocumento.CNH, TipoDocumento.PASSAPORTE);

    private static final String CODIGO_DOCUMENTO_FALTANDO = "ONB-400-005";

    private final SolicitacaoOnboardingRepository solicitacaoRepository;
    private final DocumentoCadastralRepository documentoRepository;
    private final KycProvider kycProvider;
    private final ApplicationEventPublisher eventPublisher;

    public IniciarVerificacaoKycUseCase(
            SolicitacaoOnboardingRepository solicitacaoRepository,
            DocumentoCadastralRepository documentoRepository,
            KycProvider kycProvider,
            ApplicationEventPublisher eventPublisher) {
        this.solicitacaoRepository = solicitacaoRepository;
        this.documentoRepository = documentoRepository;
        this.kycProvider = kycProvider;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public SolicitacaoOnboarding executar(
            UUID solicitacaoId, UUID usuarioAutenticadoId, boolean isAdmin, String correlationId) {
        SolicitacaoOnboarding solicitacao = solicitacaoRepository
                .findById(solicitacaoId)
                .orElseThrow(() -> new OnboardingNaoEncontradoException(solicitacaoId));
        if (!isAdmin && !solicitacao.getUsuarioId().equals(usuarioAutenticadoId)) {
            throw new AccessDeniedException("Solicitacao nao pertence ao usuario autenticado");
        }

        // Valida status ANTES de qualquer side effect externo (chamada Celcoin).
        // Evita re-disparo em solicitacao ja em verificacao ou ja finalizada.
        solicitacao.validarPodeIniciarVerificacao();

        List<DocumentoCadastral> documentos = documentoRepository.findBySolicitacaoId(solicitacaoId);
        boolean temIdentidade = documentos.stream().anyMatch(d -> IDENTIDADE.contains(d.getTipo()));
        boolean temSelfie = documentos.stream().anyMatch(d -> d.getTipo() == TipoDocumento.SELFIE);
        if (!temIdentidade || !temSelfie) {
            throw new ValidacaoException(
                    CODIGO_DOCUMENTO_FALTANDO,
                    "Documentos minimos ausentes: e necessario 1 documento de identidade (RG/CNH/PASSAPORTE) + 1 SELFIE");
        }

        String idempotencyKey = solicitacaoId + ":" + solicitacao.getRevisaoDocumentos();
        String mdcPrevio = MDC.get(IdempotencyKeyInterceptor.MDC_IDEMPOTENCY_KEY);
        MDC.put(IdempotencyKeyInterceptor.MDC_IDEMPOTENCY_KEY, idempotencyKey);
        try {
            RespostaInicioVerificacao resposta = kycProvider.iniciarVerificacao(
                    new RequisicaoVerificacaoKyc(
                            solicitacaoId,
                            solicitacao.getUsuarioId(),
                            solicitacao.getCpf(),
                            solicitacao.getNomeCompleto(),
                            solicitacao.getDataNascimento(),
                            documentos.stream()
                                    .map(d -> new RequisicaoVerificacaoKyc.DocumentoMetadados(
                                            d.getTipo(), d.getSha256(), d.getTamanhoBytes(), d.getMimeType()))
                                    .toList()),
                    correlationId);
            solicitacao.marcarEmVerificacao(resposta.idVerificacaoExterna());
            solicitacaoRepository.save(solicitacao);
            // Evento de dominio carrega o dono da solicitacao (audit trail), nao quem disparou.
            eventPublisher.publishEvent(new VerificacaoKycDisparadaEvent(
                    solicitacaoId, solicitacao.getUsuarioId(), resposta.idVerificacaoExterna()));
            return solicitacao;
        } finally {
            if (mdcPrevio == null) {
                MDC.remove(IdempotencyKeyInterceptor.MDC_IDEMPOTENCY_KEY);
            } else {
                MDC.put(IdempotencyKeyInterceptor.MDC_IDEMPOTENCY_KEY, mdcPrevio);
            }
        }
    }
}
