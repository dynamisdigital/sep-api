package com.dynamis.sep_api.onboarding.application.port.out;

import com.dynamis.sep_api.onboarding.application.port.out.dto.RequisicaoPld;
import com.dynamis.sep_api.onboarding.application.port.out.dto.RespostaPld;

/**
 * Port de saida para o provedor externo de PLD (Provider Pattern, ADR 0004). Consulta as 4 bases
 * obrigatorias por Lei 9.613/1998 + Resolucao CMN 4.656/2018: COAF, OFAC, INTERPOL e MTE.
 *
 * <p>Adapters:
 *
 * <ul>
 *   <li>{@code FakeBackgroundCheckProvider} — dev/test; default limpo em todas as bases.
 *   <li>{@code CelcoinBackgroundCheckProvider} — adapter HTTP real (Celcoin Background Check).
 * </ul>
 *
 * <p>Selecao por {@code app.pld.provider} (valores: {@code fake} ou {@code celcoin}).
 *
 * <p>Hit em qualquer base bloqueia onboarding (status {@code REPROVADO_PLD}). Detalhes de hit
 * jamais aparecem em logs, audit publico ou respostas REST.
 */
public interface BackgroundCheckProvider {

    /** Consulta PLD para pessoa fisica (PF ou representante legal). */
    RespostaPld consultarPessoa(RequisicaoPld requisicao, String correlationId);

    /** Consulta PLD para pessoa juridica. */
    RespostaPld consultarEmpresa(RequisicaoPld requisicao, String correlationId);
}
