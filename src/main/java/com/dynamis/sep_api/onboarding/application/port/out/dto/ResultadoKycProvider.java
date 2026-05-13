package com.dynamis.sep_api.onboarding.application.port.out.dto;

import com.dynamis.sep_api.onboarding.domain.vo.StatusOnboarding;

/**
 * Resultado de uma consulta ao {@link com.dynamis.sep_api.onboarding.application.port.out.KycProvider}.
 *
 * <p>Sealed para distinguir explicitamente entre verificacao ainda em andamento e verificacao
 * concluida — evita que o caller produza um {@code ResultadoVerificacao} (entidade de dominio
 * 1:1, status final obrigatorio) prematuramente.
 *
 * <p>Sempre carrega o {@link #payloadProvider} bruto para trilha auditavel.
 */
public sealed interface ResultadoKycProvider permits ResultadoKycProvider.EmAndamento, ResultadoKycProvider.Finalizado {

    String payloadProvider();

    /** Provider ainda processando — caller NAO deve finalizar a solicitacao. */
    record EmAndamento(String payloadProvider) implements ResultadoKycProvider {}

    /**
     * Provider concluiu — caller pode persistir {@code ResultadoVerificacao} e transicionar a
     * solicitacao para o status final.
     */
    record Finalizado(StatusOnboarding statusFinal, String motivo, String payloadProvider)
            implements ResultadoKycProvider {

        public Finalizado {
            if (statusFinal == null || !statusFinal.isFinal()) {
                throw new IllegalArgumentException(
                        "Finalizado exige StatusOnboarding final (APROVADO/REPROVADO/PENDENCIA); recebido: "
                                + statusFinal);
            }
        }
    }
}
