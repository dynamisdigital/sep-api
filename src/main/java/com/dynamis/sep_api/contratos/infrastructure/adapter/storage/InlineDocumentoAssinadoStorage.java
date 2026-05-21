package com.dynamis.sep_api.contratos.infrastructure.adapter.storage;

import com.dynamis.sep_api.contratos.application.port.out.DocumentoAssinadoStorage;
import com.dynamis.sep_api.contratos.infrastructure.persistence.DocumentoAssinadoBlob;
import com.dynamis.sep_api.contratos.infrastructure.persistence.DocumentoAssinadoBlobRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Adapter inline do {@link DocumentoAssinadoStorage} (Sprint 11). Persiste o PDF em coluna BYTEA
 * da tabela {@code documento_assinado_blob}; pathStorage e o {@code UUID} serializado do blob.
 *
 * <p>Roadmap Epic 16: substituir por adapter S3/MinIO. O dominio nao muda — apenas a impl deste
 * port. Esta classe nao trata aspectos cripto (encryption-at-rest) — fica a cargo do Postgres
 * (CMK no RDS) ou do bucket S3 quando migrar.
 */
@Component
public class InlineDocumentoAssinadoStorage implements DocumentoAssinadoStorage {

    private final DocumentoAssinadoBlobRepository blobRepository;

    public InlineDocumentoAssinadoStorage(DocumentoAssinadoBlobRepository blobRepository) {
        this.blobRepository = blobRepository;
    }

    @Override
    @Transactional
    public String salvar(byte[] conteudo) {
        DocumentoAssinadoBlob blob = blobRepository.save(DocumentoAssinadoBlob.criar(conteudo));
        return blob.getId().toString();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<byte[]> carregar(String pathStorage) {
        UUID id;
        try {
            id = UUID.fromString(pathStorage);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        return blobRepository.findById(id).map(DocumentoAssinadoBlob::getConteudo);
    }
}
