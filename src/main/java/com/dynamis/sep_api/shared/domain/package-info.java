/**
 * Modulo Shared - Camada de Dominio.
 *
 * <p>Responsabilidade do modulo: Excecoes, auditoria, configuracoes transversais, ApiExceptionHandler, ErrorResponseDto, base de adapters HTTP (RestClient + Resilience4j), Webhook Receiver pattern, utilitarios e tipos comuns.
 *
 * <p>Detalhes desta camada: Camada de Dominio. Contem entidades, value objects, enums, sealed types, eventos de dominio e regras centrais. Sem dependencia de Spring, JPA ou frameworks de infraestrutura. Sub-pacotes esperados: model, event, exception, vo.
 */
package com.dynamis.sep_api.shared.domain;
