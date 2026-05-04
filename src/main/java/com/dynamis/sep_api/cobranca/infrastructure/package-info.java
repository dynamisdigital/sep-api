/**
 * Modulo Cobranca - Camada de Infraestrutura.
 *
 * <p>Responsabilidade do modulo: Parcelas, vencimentos, cobranca e inadimplencia. Consome o modulo escrow para registrar recebimentos.
 *
 * <p>Detalhes desta camada: Camada de Infraestrutura. Repositories JPA, adapters concretos das portas de saida (Celcoin<X>Provider, Fake<X>Provider), integracoes externas e configuracoes de framework. Sub-pacotes esperados: persistence, adapter, config.
 */
package com.dynamis.sep_api.cobranca.infrastructure;
