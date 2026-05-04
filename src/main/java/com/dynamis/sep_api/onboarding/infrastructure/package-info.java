/**
 * Modulo Onboarding - Camada de Infraestrutura.
 *
 * <p>Responsabilidade do modulo: KYC/KYB, documentos e validacoes cadastrais. Consome KycProvider/KybProvider/BackgroundCheckProvider via Provider Pattern.
 *
 * <p>Detalhes desta camada: Camada de Infraestrutura. Repositories JPA, adapters concretos das portas de saida (Celcoin<X>Provider, Fake<X>Provider), integracoes externas e configuracoes de framework. Sub-pacotes esperados: persistence, adapter, config.
 */
package com.dynamis.sep_api.onboarding.infrastructure;
