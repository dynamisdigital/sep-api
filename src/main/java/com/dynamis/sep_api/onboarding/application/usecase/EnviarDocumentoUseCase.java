package com.dynamis.sep_api.onboarding.application.usecase;

import com.dynamis.sep_api.onboarding.application.dto.DocumentoUploadCommand;
import com.dynamis.sep_api.onboarding.application.port.out.DocumentoStorage;
import com.dynamis.sep_api.onboarding.domain.event.DocumentoCadastralEnviadoEvent;
import com.dynamis.sep_api.onboarding.domain.exception.OnboardingNaoEncontradoException;
import com.dynamis.sep_api.onboarding.domain.model.DocumentoCadastral;
import com.dynamis.sep_api.onboarding.domain.model.SolicitacaoOnboarding;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.SolicitacaoOnboardingRepository;
import com.dynamis.sep_api.shared.exception.ValidacaoException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

/**
 * Anexa um documento cadastral a solicitacao. Valida MIME, tamanho, ownership e transiciona o
 * status para {@code DOCUMENTOS_RECEBIDOS} no primeiro upload.
 */
@Service
public class EnviarDocumentoUseCase {

    private static final long TAMANHO_MAXIMO_BYTES = 10L * 1024 * 1024;
    private static final Set<String> MIMES_ACEITOS = Set.of("image/jpeg", "image/png", "application/pdf");

    private static final String CODIGO_MIME_INVALIDO = "ONB-400-003";
    private static final String CODIGO_TAMANHO_EXCEDIDO = "ONB-400-004";

    private final SolicitacaoOnboardingRepository solicitacaoRepository;
    private final DocumentoStorage storage;
    private final ApplicationEventPublisher eventPublisher;

    public EnviarDocumentoUseCase(
            SolicitacaoOnboardingRepository solicitacaoRepository,
            DocumentoStorage storage,
            ApplicationEventPublisher eventPublisher) {
        this.solicitacaoRepository = solicitacaoRepository;
        this.storage = storage;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public DocumentoCadastral executar(
            UUID solicitacaoId, UUID usuarioAutenticadoId, boolean isAdmin, DocumentoUploadCommand cmd) {
        if (cmd == null || cmd.conteudo() == null || cmd.conteudo().length == 0) {
            throw new ValidacaoException(CODIGO_TAMANHO_EXCEDIDO, "Conteudo do documento e obrigatorio");
        }
        if (!MIMES_ACEITOS.contains(cmd.mimeType())) {
            throw new ValidacaoException(
                    CODIGO_MIME_INVALIDO, "MIME nao suportado: " + cmd.mimeType() + ". Aceitos: " + MIMES_ACEITOS);
        }
        if (cmd.conteudo().length > TAMANHO_MAXIMO_BYTES) {
            throw new ValidacaoException(
                    CODIGO_TAMANHO_EXCEDIDO, "Documento excede tamanho maximo de " + TAMANHO_MAXIMO_BYTES + " bytes");
        }

        SolicitacaoOnboarding solicitacao = solicitacaoRepository
                .findById(solicitacaoId)
                .orElseThrow(() -> new OnboardingNaoEncontradoException(solicitacaoId));
        if (!isAdmin && !solicitacao.getUsuarioId().equals(usuarioAutenticadoId)) {
            throw new AccessDeniedException("Solicitacao nao pertence ao usuario autenticado");
        }

        solicitacao.registrarDocumentoEnviado();
        solicitacaoRepository.save(solicitacao);

        String sha256 = calcularSha256(cmd.conteudo());
        DocumentoCadastral documento = DocumentoCadastral.criar(
                solicitacaoId, cmd.tipo(), cmd.conteudo(), cmd.mimeType(), cmd.nomeOriginal(), sha256);
        DocumentoCadastral salvo = storage.salvar(documento);

        // Evento de dominio carrega o usuario dono da solicitacao para auditoria, nao quem
        // disparou (poderia ser ADMIN operando em nome do cliente).
        eventPublisher.publishEvent(new DocumentoCadastralEnviadoEvent(
                solicitacaoId, solicitacao.getUsuarioId(), salvo.getId(), salvo.getTipo(), sha256));
        return salvo;
    }

    private static String calcularSha256(byte[] dados) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(dados));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel no runtime", e);
        }
    }
}
