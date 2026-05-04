/**
 * Modulo Credito - Camada de Infraestrutura.
 *
 * <p>Responsabilidade do modulo: Proposta, analise de credito, parecer e decisao. Pode consumir OpenFinanceProvider e CreditoBureauProvider via Provider Pattern.
 *
 * <p>Detalhes desta camada: Camada de Infraestrutura. Repositories JPA, adapters concretos das portas de saida (Celcoin<X>Provider, Fake<X>Provider), integracoes externas e configuracoes de framework. Sub-pacotes esperados: persistence, adapter, config.
 */
package com.dynamis.sep_api.credito.infrastructure;
