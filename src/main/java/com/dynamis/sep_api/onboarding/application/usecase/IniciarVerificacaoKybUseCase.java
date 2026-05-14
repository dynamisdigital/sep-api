package com.dynamis.sep_api.onboarding.application.usecase;

import com.dynamis.sep_api.onboarding.application.port.out.KybProvider;
import com.dynamis.sep_api.onboarding.application.port.out.dto.RepresentanteLegalProviderDto;
import com.dynamis.sep_api.onboarding.application.port.out.dto.RequisicaoKyb;
import com.dynamis.sep_api.onboarding.application.port.out.dto.RespostaKyb;
import com.dynamis.sep_api.onboarding.domain.event.KybFinalizadoEvent;
import com.dynamis.sep_api.onboarding.domain.exception.KybNaoEncontradoException;
import com.dynamis.sep_api.onboarding.domain.exception.OnboardingNaoEncontradoException;
import com.dynamis.sep_api.onboarding.domain.model.ConsultaCNPJ;
import com.dynamis.sep_api.onboarding.domain.model.KybEmpresa;
import com.dynamis.sep_api.onboarding.domain.model.RepresentanteLegal;
import com.dynamis.sep_api.onboarding.domain.model.SolicitacaoOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.Cpf;
import com.dynamis.sep_api.onboarding.domain.vo.StatusOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.TipoSolicitante;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.ConsultaCNPJRepository;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.KybEmpresaRepository;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.RepresentanteLegalRepository;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.SolicitacaoOnboardingRepository;
import com.dynamis.sep_api.shared.exception.ValidacaoException;
import com.dynamis.sep_api.shared.integration.IdempotencyKeyInterceptor;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Dispara verificacao KYB no provider externo. Aceita apenas solicitacao tipo {@code EMPRESA}.
 *
 * <p>Idempotency-Key deterministica: {@code solicitacaoId + ":kyb:" + revisaoDocumentos}.
 *
 * <p>Persiste {@link ConsultaCNPJ} (1:1 com KybEmpresa) e {@link RepresentanteLegal} (N:1) com base
 * no retorno do provider. Situacao diferente de {@code ATIVA} reprova KYB e NAO dispara PLD.
 * Situacao {@code ATIVA} finaliza KYB como {@code APROVADO} (pre-PLD); orquestracao do PLD acontece
 * em {@code PldOrchestrationListener} via {@link KybFinalizadoEvent}.
 */
@Service
public class IniciarVerificacaoKybUseCase {

    private static final String CODIGO_TIPO_INVALIDO = "ONB-400-008";

    private final SolicitacaoOnboardingRepository solicitacaoRepository;
    private final KybEmpresaRepository kybRepository;
    private final ConsultaCNPJRepository consultaCnpjRepository;
    private final RepresentanteLegalRepository representanteRepository;
    private final KybProvider kybProvider;
    private final ApplicationEventPublisher eventPublisher;

    public IniciarVerificacaoKybUseCase(
            SolicitacaoOnboardingRepository solicitacaoRepository,
            KybEmpresaRepository kybRepository,
            ConsultaCNPJRepository consultaCnpjRepository,
            RepresentanteLegalRepository representanteRepository,
            KybProvider kybProvider,
            ApplicationEventPublisher eventPublisher) {
        this.solicitacaoRepository = solicitacaoRepository;
        this.kybRepository = kybRepository;
        this.consultaCnpjRepository = consultaCnpjRepository;
        this.representanteRepository = representanteRepository;
        this.kybProvider = kybProvider;
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
        if (solicitacao.getTipo() != TipoSolicitante.EMPRESA) {
            throw new ValidacaoException(CODIGO_TIPO_INVALIDO, "Solicitacao nao e do tipo EMPRESA");
        }
        solicitacao.validarPodeIniciarVerificacao();

        KybEmpresa kyb = kybRepository
                .findBySolicitacaoId(solicitacaoId)
                .orElseThrow(() -> new KybNaoEncontradoException(solicitacaoId));

        String idempotencyKey = solicitacaoId + ":kyb:" + solicitacao.getRevisaoDocumentos();
        String mdcPrevio = MDC.get(IdempotencyKeyInterceptor.MDC_IDEMPOTENCY_KEY);
        MDC.put(IdempotencyKeyInterceptor.MDC_IDEMPOTENCY_KEY, idempotencyKey);
        try {
            RespostaKyb resposta = kybProvider.consultarCnpj(
                    new RequisicaoKyb(
                            solicitacaoId, solicitacao.getUsuarioId(), kyb.getCnpj(), kyb.getRazaoSocial(), List.of()),
                    correlationId);

            // Marca em verificacao apenas pra registrar trilha; verificacao KYB e sincrona.
            solicitacao.marcarEmVerificacao("kyb-sync-" + solicitacaoId);

            persistirConsultaCnpj(kyb.getId(), resposta);

            StatusOnboarding statusFinal;
            if (resposta.situacaoCadastral().habilitaProgressao()) {
                kyb.atualizarDadosCadastrais(resposta.razaoSocial(), resposta.nomeFantasia());
                persistirRepresentantes(kyb.getId(), resposta.representantes());
                statusFinal = StatusOnboarding.APROVADO;
            } else {
                statusFinal = StatusOnboarding.REPROVADO;
            }
            solicitacao.finalizar(statusFinal);

            kybRepository.save(kyb);
            solicitacaoRepository.save(solicitacao);
            eventPublisher.publishEvent(
                    new KybFinalizadoEvent(solicitacaoId, solicitacao.getUsuarioId(), statusFinal, kyb.getId()));
            return solicitacao;
        } finally {
            if (mdcPrevio == null) {
                MDC.remove(IdempotencyKeyInterceptor.MDC_IDEMPOTENCY_KEY);
            } else {
                MDC.put(IdempotencyKeyInterceptor.MDC_IDEMPOTENCY_KEY, mdcPrevio);
            }
        }
    }

    private void persistirConsultaCnpj(UUID kybEmpresaId, RespostaKyb resposta) {
        ConsultaCNPJ consulta = ConsultaCNPJ.registrar(
                kybEmpresaId,
                resposta.situacaoCadastral(),
                resposta.razaoSocial(),
                resposta.nomeFantasia(),
                resposta.cnaePrincipal(),
                resposta.cnaesSecundarios(),
                resposta.capitalSocial(),
                resposta.dataAbertura(),
                resposta.payloadProvider());
        consultaCnpjRepository.save(consulta);
    }

    private void persistirRepresentantes(UUID kybEmpresaId, List<RepresentanteLegalProviderDto> representantes) {
        if (representantes == null || representantes.isEmpty()) return;
        for (RepresentanteLegalProviderDto r : representantes) {
            try {
                Cpf cpfVo = new Cpf(r.cpf());
                representanteRepository.save(RepresentanteLegal.criar(kybEmpresaId, r.nome(), cpfVo, r.cargo()));
            } catch (IllegalArgumentException ignored) {
                // CPF invalido vindo do provider: descartar silenciosamente — payload bruto ja persistido
                // em consulta_cnpj.payload_provider para trilha auditavel.
            }
        }
    }
}
