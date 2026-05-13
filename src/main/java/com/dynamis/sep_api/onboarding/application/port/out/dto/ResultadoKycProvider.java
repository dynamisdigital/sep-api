package com.dynamis.sep_api.onboarding.application.port.out.dto;

import com.dynamis.sep_api.onboarding.domain.vo.StatusOnboarding;

/**
 * Resultado retornado pelo {@link com.dynamis.sep_api.onboarding.application.port.out.KycProvider}
 * apos consulta ou consumo de webhook.
 *
 * @param statusFinal sempre um status final ({@code APROVADO}, {@code REPROVADO}, {@code
 *     PENDENCIA}); validado no factory de {@code ResultadoVerificacao}.
 * @param motivo descricao do provider quando reprovado/pendencia.
 * @param payloadProvider payload bruto JSON do provider — persistido em {@code
 *     resultado_verificacao.payload_provider}. NUNCA logar nem replicar em audit log.
 */
public record ResultadoKycProvider(StatusOnboarding statusFinal, String motivo, String payloadProvider) {}
