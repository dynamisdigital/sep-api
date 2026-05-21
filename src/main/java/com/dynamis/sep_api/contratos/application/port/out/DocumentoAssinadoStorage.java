package com.dynamis.sep_api.contratos.application.port.out;

import java.util.Optional;

/**
 * Port de saida para storage do PDF assinado (Sprint 11 Task 11.2/11.3).
 *
 * <p>Abstrai a persistencia binaria do documento. Implementacao desta sprint
 * (`InlineDocumentoAssinadoStorage`) grava em coluna BYTEA na propria tabela
 * {@code documento_assinado}; roadmap Epic 16 substitui por S3/MinIO trocando apenas o adapter.
 * Use cases de download/regeneracao acessam o PDF exclusivamente por aqui — nunca por leitura
 * direta de coluna JPA.
 *
 * <p>Contrato:
 * <ul>
 *   <li>{@link #salvar} retorna o {@code pathStorage} opaco (referencia interna do adapter);
 *   <li>{@link #carregar} aceita esse path opaco e retorna bytes, ou {@link Optional#empty()}
 *       quando o documento foi expurgado/movido por politica de retencao.
 * </ul>
 */
public interface DocumentoAssinadoStorage {

    /**
     * Persiste o PDF assinado e devolve o path opaco usado para recuperacao posterior. O path nao
     * deve ser interpretado fora do adapter (nao usar como URL, nem como chave de negocio).
     */
    String salvar(byte[] conteudo);

    Optional<byte[]> carregar(String pathStorage);
}
