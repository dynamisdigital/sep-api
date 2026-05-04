/**
 * Modulo Escrow - Camada de Infraestrutura.
 *
 * <p>Responsabilidade do modulo: Contas escrow, wallets por proposta/operacao, movimentacoes e segregacao patrimonial obrigatoria por Resolucao CMN 4.656/2018. Consome EscrowProvider via Provider Pattern.
 *
 * <p>Detalhes desta camada: Camada de Infraestrutura. Repositories JPA, adapters concretos das portas de saida (Celcoin<X>Provider, Fake<X>Provider), integracoes externas e configuracoes de framework. Sub-pacotes esperados: persistence, adapter, config.
 */
package com.dynamis.sep_api.escrow.infrastructure;
