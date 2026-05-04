/**
 * Modulo Credores - Camada de Infraestrutura.
 *
 * <p>Responsabilidade do modulo: Jornada da empresa credora: carteira, aportes e operacoes financiadas. Consome o modulo escrow para acompanhar carteira.
 *
 * <p>Detalhes desta camada: Camada de Infraestrutura. Repositories JPA, adapters concretos das portas de saida (Celcoin<X>Provider, Fake<X>Provider), integracoes externas e configuracoes de framework. Sub-pacotes esperados: persistence, adapter, config.
 */
package com.dynamis.sep_api.credores.infrastructure;
