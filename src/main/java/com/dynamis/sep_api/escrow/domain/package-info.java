/**
 * Modulo Escrow - Camada de Dominio.
 *
 * <p>Responsabilidade do modulo: Contas escrow, wallets por proposta/operacao, movimentacoes e segregacao patrimonial obrigatoria por Resolucao CMN 4.656/2018. Consome EscrowProvider via Provider Pattern.
 *
 * <p>Detalhes desta camada: Camada de Dominio. Contem entidades, value objects, enums, sealed types, eventos de dominio e regras centrais. Sem dependencia de Spring, JPA ou frameworks de infraestrutura. Sub-pacotes esperados: model, event, exception, vo.
 */
package com.dynamis.sep_api.escrow.domain;
