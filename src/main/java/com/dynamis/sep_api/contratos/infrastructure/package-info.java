/**
 * Modulo Contratos - Camada de Infraestrutura.
 *
 * <p>Responsabilidade do modulo: Formalizacao, aceite, assinatura e status contratual. Consome AssinaturaDigitalProvider e CcbProvider via Provider Pattern.
 *
 * <p>Detalhes desta camada: Camada de Infraestrutura. Repositories JPA, adapters concretos das portas de saida (Celcoin<X>Provider, Fake<X>Provider), integracoes externas e configuracoes de framework. Sub-pacotes esperados: persistence, adapter, config.
 */
package com.dynamis.sep_api.contratos.infrastructure;
