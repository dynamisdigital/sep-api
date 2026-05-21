package com.dynamis.sep_api.contratos.application.usecase;

import com.dynamis.sep_api.contratos.application.port.out.DocumentoAssinadoStorage;
import com.dynamis.sep_api.contratos.domain.exception.ContratoAssinaturaIndisponivelException;
import com.dynamis.sep_api.contratos.domain.model.Contrato;
import com.dynamis.sep_api.contratos.domain.model.DocumentoAssinado;
import com.dynamis.sep_api.contratos.domain.model.EnvelopeAssinatura;
import com.dynamis.sep_api.contratos.domain.vo.StatusFormalizacao;
import com.dynamis.sep_api.contratos.infrastructure.persistence.DocumentoAssinadoRepository;
import com.dynamis.sep_api.contratos.infrastructure.persistence.EnvelopeAssinaturaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

/**
 * Caso de uso: recupera bytes + metadados do PDF assinado do contrato (Sprint 11 Task 11.5).
 * Acessivel apenas quando contrato esta em {@code ASSINADO}; autorizacao (ownership/role) eh
 * responsabilidade do controller (Task 11.7).
 *
 * <p>Audit log {@code DOCUMENTO_ASSINADO_BAIXADO} fica pra Task 11.8.
 */
@Service
public class BaixarDocumentoAssinadoUseCase {

    private final ContratoLoaderService contratoLoader;
    private final EnvelopeAssinaturaRepository envelopeRepository;
    private final DocumentoAssinadoRepository documentoRepository;
    private final DocumentoAssinadoStorage storage;

    public BaixarDocumentoAssinadoUseCase(
            ContratoLoaderService contratoLoader,
            EnvelopeAssinaturaRepository envelopeRepository,
            DocumentoAssinadoRepository documentoRepository,
            DocumentoAssinadoStorage storage) {
        this.contratoLoader = contratoLoader;
        this.envelopeRepository = envelopeRepository;
        this.documentoRepository = documentoRepository;
        this.storage = storage;
    }

    @Transactional(readOnly = true)
    public Resultado executar(UUID contratoId) {
        Objects.requireNonNull(contratoId, "contratoId obrigatorio");
        Contrato contrato = contratoLoader.carregar(contratoId);
        if (contrato.getStatus() != StatusFormalizacao.ASSINADO) {
            throw new ContratoAssinaturaIndisponivelException(contratoId, "status=" + contrato.getStatus());
        }
        EnvelopeAssinatura envelope = envelopeRepository
                .findByContratoId(contratoId)
                .orElseThrow(() -> new ContratoAssinaturaIndisponivelException(contratoId, "envelope ausente"));
        DocumentoAssinado documento = documentoRepository
                .findByEnvelopeId(envelope.getId())
                .orElseThrow(() -> new ContratoAssinaturaIndisponivelException(contratoId, "documento ausente"));
        byte[] bytes = storage.carregar(documento.getPathStorage())
                .orElseThrow(() -> new ContratoAssinaturaIndisponivelException(
                        contratoId, "binario expurgado pathStorage=" + documento.getPathStorage()));
        return new Resultado(documento, bytes);
    }

    public record Resultado(DocumentoAssinado documento, byte[] conteudo) {}
}
