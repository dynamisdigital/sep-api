/**
 * Modulo Notificacao - Camada de Dominio.
 *
 * <p>Responsabilidade do modulo: historico de notificacoes por usuario, canal in-app da central e
 * idempotencia por origem (Sprint 38, ADR 0021). A regua de cobranca segue no modulo cobranca.
 *
 * <p>Detalhes desta camada: Camada de Dominio. Contem entidades, value objects, enums, sealed types, eventos de dominio e regras centrais. Sem dependencia de Spring, JPA ou frameworks de infraestrutura. Sub-pacotes esperados: model, vo.
 */
package com.dynamis.sep_api.notificacao.domain;
