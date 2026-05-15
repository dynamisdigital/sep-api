package com.dynamis.sep_api.onboarding.application.usecase;

import com.dynamis.sep_api.onboarding.application.dto.StatusOnboardingEmpresaView;
import com.dynamis.sep_api.onboarding.domain.exception.KybNaoEncontradoException;
import com.dynamis.sep_api.onboarding.domain.exception.OnboardingNaoEncontradoException;
import com.dynamis.sep_api.onboarding.domain.model.KybEmpresa;
import com.dynamis.sep_api.onboarding.domain.model.RepresentanteLegal;
import com.dynamis.sep_api.onboarding.domain.model.ResultadoVerificacao;
import com.dynamis.sep_api.onboarding.domain.model.SolicitacaoOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.TipoSolicitante;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.DocumentoCadastralRepository;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.KybEmpresaRepository;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.RepresentanteLegalRepository;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.ResultadoVerificacaoRepository;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.SolicitacaoOnboardingRepository;
import com.dynamis.sep_api.shared.exception.ValidacaoException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Consulta composta da solicitacao PJ — agrega status, dados cadastrais KYB, documentos,
 * representantes legais e resultado da verificacao. Cliente le apenas a propria solicitacao; admin
 * le qualquer. Rejeita solicitacao tipo {@code PESSOA}.
 */
@Service
public class ConsultarStatusOnboardingEmpresaUseCase {

    private static final String CODIGO_TIPO_INVALIDO = "ONB-400-008";

    private final SolicitacaoOnboardingRepository solicitacaoRepository;
    private final KybEmpresaRepository kybRepository;
    private final DocumentoCadastralRepository documentoRepository;
    private final RepresentanteLegalRepository representanteRepository;
    private final ResultadoVerificacaoRepository resultadoRepository;

    public ConsultarStatusOnboardingEmpresaUseCase(
            SolicitacaoOnboardingRepository solicitacaoRepository,
            KybEmpresaRepository kybRepository,
            DocumentoCadastralRepository documentoRepository,
            RepresentanteLegalRepository representanteRepository,
            ResultadoVerificacaoRepository resultadoRepository) {
        this.solicitacaoRepository = solicitacaoRepository;
        this.kybRepository = kybRepository;
        this.documentoRepository = documentoRepository;
        this.representanteRepository = representanteRepository;
        this.resultadoRepository = resultadoRepository;
    }

    @Transactional(readOnly = true)
    public StatusOnboardingEmpresaView executar(UUID solicitacaoId, UUID usuarioAutenticadoId, boolean isAdmin) {
        SolicitacaoOnboarding solicitacao = solicitacaoRepository
                .findById(solicitacaoId)
                .orElseThrow(() -> new OnboardingNaoEncontradoException(solicitacaoId));
        if (!isAdmin && !solicitacao.getUsuarioId().equals(usuarioAutenticadoId)) {
            throw new AccessDeniedException("Solicitacao nao pertence ao usuario autenticado");
        }
        if (solicitacao.getTipo() != TipoSolicitante.EMPRESA) {
            throw new ValidacaoException(CODIGO_TIPO_INVALIDO, "Solicitacao nao e do tipo EMPRESA");
        }

        KybEmpresa kyb = kybRepository
                .findBySolicitacaoId(solicitacaoId)
                .orElseThrow(() -> new KybNaoEncontradoException(solicitacaoId));

        var documentos = documentoRepository.findBySolicitacaoId(solicitacaoId).stream()
                .map(d -> new StatusOnboardingEmpresaView.DocumentoEnviado(
                        d.getId(), d.getTipo(), d.getDataEnvio(), d.getSha256()))
                .toList();

        var representantes = representanteRepository.findByKybEmpresaId(kyb.getId()).stream()
                .map(this::mapearRepresentante)
                .toList();

        StatusOnboardingEmpresaView.ResultadoView resultadoView = resultadoRepository
                .findBySolicitacaoId(solicitacaoId)
                .map(this::mapearResultado)
                .orElse(null);

        var dadosEmpresa = new StatusOnboardingEmpresaView.DadosEmpresaView(
                kyb.getCnpj(), kyb.getRazaoSocial(), kyb.getNomeFantasia(), kyb.getTipoSocietario(), kyb.getPorte());

        return new StatusOnboardingEmpresaView(
                solicitacao.getId(),
                solicitacao.getStatus(),
                solicitacao.getDataCriacao(),
                solicitacao.getDataModificacao(),
                dadosEmpresa,
                documentos,
                representantes,
                resultadoView);
    }

    private StatusOnboardingEmpresaView.RepresentanteView mapearRepresentante(RepresentanteLegal r) {
        return new StatusOnboardingEmpresaView.RepresentanteView(
                r.getId(), r.getNome(), r.getCpf(), r.getCargo(), r.getStatusPld(), r.getDataConsultaPld());
    }

    private StatusOnboardingEmpresaView.ResultadoView mapearResultado(ResultadoVerificacao r) {
        return new StatusOnboardingEmpresaView.ResultadoView(r.getStatusFinal(), r.getMotivo(), r.getDataResultado());
    }
}
