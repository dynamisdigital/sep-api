package com.dynamis.sep_api.onboarding.application.port.out;

import com.dynamis.sep_api.onboarding.application.port.out.dto.RequisicaoKyb;
import com.dynamis.sep_api.onboarding.application.port.out.dto.RespostaKyb;

/**
 * Port de saida para o provedor externo de KYB PJ (Provider Pattern, ADR 0004). Adapters:
 *
 * <ul>
 *   <li>{@code FakeKybProvider} — dev/test sem credenciais; sempre situacao {@code ATIVA}.
 *   <li>{@code CelcoinKybProvider} — adapter HTTP real (Celcoin Onboarding KYB) com Resilience4j.
 * </ul>
 *
 * <p>Selecao por {@code app.kyb.provider} (valores: {@code fake} ou {@code celcoin}).
 *
 * <p>KYB e sincrono: uma unica chamada devolve dados cadastrais + representantes legais. Webhook
 * existe apenas pra atualizar estado em caso de operacao manual no painel Celcoin.
 */
public interface KybProvider {

    /** Consulta CNPJ no provider externo e devolve dados cadastrais + representantes legais. */
    RespostaKyb consultarCnpj(RequisicaoKyb requisicao, String correlationId);
}
