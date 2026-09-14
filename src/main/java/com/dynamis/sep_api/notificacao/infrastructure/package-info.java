/**
 * Modulo Notificacao - Camada de Infraestrutura.
 *
 * <p>Responsabilidade do modulo: historico de notificacoes por usuario, canal in-app da central e
 * idempotencia por origem (Sprint 38, ADR 0021). A regua de cobranca segue no modulo cobranca.
 *
 * <p>Detalhes desta camada: Camada de Infraestrutura. Entidade JPA propria e mapeamento explicito para o agregado (ADR 0007), adapters concretos das portas de saida e configuracoes de framework. Sub-pacotes esperados: persistence, adapter.
 */
package com.dynamis.sep_api.notificacao.infrastructure;
