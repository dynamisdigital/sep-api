/**
 * Modulo Notificacao - Camada de Aplicacao.
 *
 * <p>Responsabilidade do modulo: historico de notificacoes por usuario, canal in-app da central e
 * idempotencia por origem (Sprint 38, ADR 0021). A regua de cobranca segue no modulo cobranca.
 *
 * <p>Detalhes desta camada: Camada de Aplicacao. Casos de uso, listeners de eventos de outros modulos e portas de saida em port.out (Provider Pattern, ADR 0004). Sub-pacotes esperados: usecase, listener, port.out.
 */
package com.dynamis.sep_api.notificacao.application;
