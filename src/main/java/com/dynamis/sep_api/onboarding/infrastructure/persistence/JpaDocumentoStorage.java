package com.dynamis.sep_api.onboarding.infrastructure.persistence;

import com.dynamis.sep_api.onboarding.application.port.out.DocumentoStorage;
import com.dynamis.sep_api.onboarding.domain.model.DocumentoCadastral;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Adapter de {@link DocumentoStorage} baseado em JPA — BYTEA inline na tabela
 * {@code documento_cadastral}.
 */
@Component
public class JpaDocumentoStorage implements DocumentoStorage {

    private final DocumentoCadastralRepository repository;

    public JpaDocumentoStorage(DocumentoCadastralRepository repository) {
        this.repository = repository;
    }

    @Override
    public DocumentoCadastral salvar(DocumentoCadastral documento) {
        return repository.save(documento);
    }

    @Override
    public List<DocumentoCadastral> listarPorSolicitacao(UUID solicitacaoId) {
        return repository.findBySolicitacaoId(solicitacaoId);
    }
}
