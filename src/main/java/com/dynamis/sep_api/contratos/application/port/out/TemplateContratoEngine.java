package com.dynamis.sep_api.contratos.application.port.out;

import com.dynamis.sep_api.contratos.application.port.out.dto.TemplateContratoRequest;
import com.dynamis.sep_api.contratos.application.port.out.dto.TemplateContratoResponse;

/**
 * Porta de saida do modulo {@code contratos}: renderiza um template textual de contrato com base
 * em variaveis fornecidas pelo use case. Implementacoes concretas (ex.: Thymeleaf) vivem no
 * pacote {@code infrastructure.template}.
 *
 * <p>A interface usa apenas tipos de dominio/DTO da port; nao deve vazar classes da engine
 * (Thymeleaf, Velocity, etc) para permitir troca de implementacao sem impacto na camada
 * application.
 */
public interface TemplateContratoEngine {

    /**
     * Renderiza o template correspondente ao {@code tipo} com as variaveis fornecidas.
     *
     * @throws TemplateContratoException se o template nao existir ou houver erro de renderizacao.
     */
    TemplateContratoResponse renderizar(TemplateContratoRequest request);
}
