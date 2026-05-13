package com.dynamis.sep_api.onboarding.application.port.out.dto;

/**
 * Resposta sincrona do KycProvider apos disparar a verificacao. O resultado final chega depois via
 * webhook (Task 6.4) ou polling via {@link
 * com.dynamis.sep_api.onboarding.application.port.out.KycProvider#consultarResultado}.
 *
 * @param idVerificacaoExterna ID retornado pelo Celcoin para correlacionar o webhook posterior.
 * @param statusInicial geralmente "PROCESSING"; valor exato depende do provider.
 */
public record RespostaInicioVerificacao(String idVerificacaoExterna, String statusInicial) {}
