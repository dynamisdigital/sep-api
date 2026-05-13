package com.dynamis.sep_api.onboarding.application.port.out;

import com.dynamis.sep_api.onboarding.application.port.out.dto.RequisicaoVerificacaoKyc;
import com.dynamis.sep_api.onboarding.application.port.out.dto.RespostaInicioVerificacao;
import com.dynamis.sep_api.onboarding.application.port.out.dto.ResultadoKycProvider;

/**
 * Port de saida para o provedor externo de KYC (Provider Pattern, ADR 0004). Adapters:
 *
 * <ul>
 *   <li>{@code FakeKycProvider} — dev/test sem credenciais; sempre {@code APROVADO}.
 *   <li>{@code CelcoinKycProvider} — adapter HTTP real (Celcoin Onboarding API) com Resilience4j.
 * </ul>
 *
 * <p>Selecao por {@code app.kyc.provider} (valores: {@code fake} ou {@code celcoin}).
 */
public interface KycProvider {

    /** Dispara a verificacao KYC no provider externo. */
    RespostaInicioVerificacao iniciarVerificacao(RequisicaoVerificacaoKyc requisicao, String correlationId);

    /** Consulta o resultado da verificacao pelo ID externo retornado em {@link #iniciarVerificacao}. */
    ResultadoKycProvider consultarResultado(String idVerificacaoExterna, String correlationId);
}
