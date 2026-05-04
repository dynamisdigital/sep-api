/**
 * Modulo Pix - Camada de Infraestrutura.
 *
 * <p>Responsabilidade do modulo: Movimentacao Pix, webhooks, conciliacao e status. Consome PixProvider e o modulo escrow.
 *
 * <p>Detalhes desta camada: Camada de Infraestrutura. Repositories JPA, adapters concretos das portas de saida (Celcoin<X>Provider, Fake<X>Provider), integracoes externas e configuracoes de framework. Sub-pacotes esperados: persistence, adapter, config.
 */
package com.dynamis.sep_api.pix.infrastructure;
