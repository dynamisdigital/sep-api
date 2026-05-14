package com.dynamis.sep_api.onboarding.application.usecase;

import com.dynamis.sep_api.onboarding.application.port.out.KybProvider;
import com.dynamis.sep_api.onboarding.application.port.out.dto.RepresentanteLegalProviderDto;
import com.dynamis.sep_api.onboarding.application.port.out.dto.RequisicaoKyb;
import com.dynamis.sep_api.onboarding.application.port.out.dto.RespostaKyb;
import com.dynamis.sep_api.onboarding.domain.event.KybFinalizadoEvent;
import com.dynamis.sep_api.onboarding.domain.exception.KybNaoEncontradoException;
import com.dynamis.sep_api.onboarding.domain.exception.OnboardingNaoEncontradoException;
import com.dynamis.sep_api.onboarding.domain.model.ConsultaCNPJ;
import com.dynamis.sep_api.onboarding.domain.model.DocumentoCadastral;
import com.dynamis.sep_api.onboarding.domain.model.KybEmpresa;
import com.dynamis.sep_api.onboarding.domain.model.RepresentanteLegal;
import com.dynamis.sep_api.onboarding.domain.model.SolicitacaoOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.Cpf;
import com.dynamis.sep_api.onboarding.domain.vo.StatusOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.TipoDocumento;
import com.dynamis.sep_api.onboarding.domain.vo.TipoSolicitante;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.ConsultaCNPJRepository;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.DocumentoCadastralRepository;
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
import java.util.Set;
import java.util.UUID;

/**
 * Dispara verificacao KYB no provider externo. Aceita apenas solicitacao tipo {@code EMPRESA}.
 *
 * <p>Documentos minimos PJ: 1 de identificacao societaria ({@code CONTRATO_SOCIAL} ou
 * {@code CCMEI}) + 1 {@code COMPROVANTE_ENDERECO}. Metadados (tipo + sha256 + tamanho + mime)
 * sao enviados ao provider; binarios ficam no banco.
 *
 * <p>Idempotency-Key deterministica: {@code solicitacaoId + ":kyb:" + revisaoDocumentos}.
 *
 * <p>Situacao diferente de {@code ATIVA} reprova KYB e NAO dispara PLD. Situacao {@code ATIVA}
 * finaliza KYB como {@code APROVADO} (pre-PLD); orquestracao do PLD acontece em
 * {@code PldOrchestrationListener} via {@link KybFinalizadoEvent}.
 */
@Service
public class IniciarVerificacaoKybUseCase {

    private static final String CODIGO_TIPO_INVALIDO = "ONB-400-008";
    private static final String CODIGO_DOCUMENTO_FALTANDO = "ONB-400-013";

    private static final Set<TipoDocumento> IDENTIFICACAO_SOCIETARIA =
            Set.of(TipoDocumento.CONTRATO_SOCIAL, TipoDocumento.CCMEI);

    private final SolicitacaoOnboardingRepository solicitacaoRepository;
    private final KybEmpresaRepository kybRepository;
    private final ConsultaCNPJRepository consultaCnpjRepository;
    private final RepresentanteLegalRepository representanteRepository;
    private final DocumentoCadastralRepository documentoRepository;
    private final KybProvider kybProvider;
    private final ApplicationEventPublisher eventPublisher;

    public IniciarVerificacaoKybUseCase(
            SolicitacaoOnboardingRepository solicitacaoRepository,
            KybEmpresaRepository kybRepository,
            ConsultaCNPJRepository consultaCnpjRepository,
            RepresentanteLegalRepository representanteRepository,
            DocumentoCadastralRepository documentoRepository,
            KybProvider kybProvider,
            ApplicationEventPublisher eventPublisher) {
        this.solicitacaoRepository = solicitacaoRepository;
        this.kybRepository = kybRepository;
        this.consultaCnpjRepository = consultaCnpjRepository;
        this.representanteRepository = representanteRepository;
        this.documentoRepository = documentoRepository;
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

        List<DocumentoCadastral> documentos = documentoRepository.findBySolicitacaoId(solicitacaoId);
        boolean temSocietario = documentos.stream().anyMatch(d -> IDENTIFICACAO_SOCIETARIA.contains(d.getTipo()));
        boolean temComprovante = documentos.stream().anyMatch(d -> d.getTipo() == TipoDocumento.COMPROVANTE_ENDERECO);
        if (!temSocietario || !temComprovante) {
            throw new ValidacaoException(
                    CODIGO_DOCUMENTO_FALTANDO,
                    "Documentos minimos PJ ausentes: 1 CONTRATO_SOCIAL ou CCMEI + 1 COMPROVANTE_ENDERECO");
        }

        String idempotencyKey = solicitacaoId + ":kyb:" + solicitacao.getRevisaoDocumentos();
        String mdcPrevio = MDC.get(IdempotencyKeyInterceptor.MDC_IDEMPOTENCY_KEY);
        MDC.put(IdempotencyKeyInterceptor.MDC_IDEMPOTENCY_KEY, idempotencyKey);
        try {
            List<RequisicaoKyb.DocumentoMetadadosKyb> metadados = documentos.stream()
                    .map(d -> new RequisicaoKyb.DocumentoMetadadosKyb(
                            d.getTipo().name(), d.getSha256(), d.getTamanhoBytes(), d.getMimeType()))
                    .toList();
            RespostaKyb resposta = kybProvider.consultarCnpj(
                    new RequisicaoKyb(
                            solicitacaoId, solicitacao.getUsuarioId(), kyb.getCnpj(), kyb.getRazaoSocial(), metadados),
                    correlationId);

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
                // CPF invalido do provider: descartar silenciosamente — payload bruto fica em
                // consulta_cnpj.payload_provider para trilha auditavel.
            }
        }
    }
}
