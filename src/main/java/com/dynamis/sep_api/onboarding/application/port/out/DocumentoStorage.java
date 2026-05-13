package com.dynamis.sep_api.onboarding.application.port.out;

import com.dynamis.sep_api.onboarding.domain.model.DocumentoCadastral;

import java.util.List;
import java.util.UUID;

/**
 * Port de saida para storage de documentos cadastrais.
 *
 * <p>Implementacao desta Sprint persiste o binario inline em {@code documento_cadastral.conteudo}
 * (BYTEA, limite 10MB por check constraint). Roadmap Epic 16: migrar para S3/MinIO mantendo
 * apenas referencia + metadados no banco.
 */
public interface DocumentoStorage {

    /** Persiste o documento e retorna a entidade ja com id e metadados gravados. */
    DocumentoCadastral salvar(DocumentoCadastral documento);

    List<DocumentoCadastral> listarPorSolicitacao(UUID solicitacaoId);
}
