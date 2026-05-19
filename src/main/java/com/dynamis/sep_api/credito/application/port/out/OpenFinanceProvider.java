package com.dynamis.sep_api.credito.application.port.out;

import com.dynamis.sep_api.credito.application.port.out.dto.MovimentacaoConsolidada;
import com.dynamis.sep_api.credito.application.port.out.dto.RequisicaoConsentimento;
import com.dynamis.sep_api.credito.application.port.out.dto.RespostaConsentimento;

/**
 * Port de saida pro provedor externo de Open Finance Brasil (Provider Pattern, ADR 0004). Sprint 9.
 *
 * <ul>
 *   <li>{@code FakeOpenFinanceProvider} — dev/test sem credenciais; cenarios deterministicos.
 *   <li>{@code CelcoinOpenFinanceProvider} — adapter HTTP real (Celcoin Finansystech) com OAuth2 +
 *       Resilience4j.
 * </ul>
 *
 * <p>Selecao por {@code app.open-finance.provider} (valores: {@code fake} ou {@code celcoin}).
 *
 * <p>DTOs do port usam linguagem de dominio — adapters convertem para formato externo. Excecoes
 * tecnicas (timeouts, 5xx) sobem como exception do RestClient; erros de negocio devem ser
 * convertidos em valor de retorno apropriado pelo adapter.
 */
public interface OpenFinanceProvider {

    /**
     * Solicita criacao de consentimento ao provedor. Retorna URL de autorizacao que o tomador deve
     * acessar (handoff redirect) + id externo usado pra correlacionar o callback posterior.
     */
    RespostaConsentimento iniciarConsentimento(RequisicaoConsentimento requisicao, String correlationId);

    /**
     * Apos o consentimento autorizado pelo tomador, consulta dados consolidados de movimentacao
     * bancaria. Resposta deve ser snapshot agregado — adapter sanitiza payload pra remover dados
     * identificaveis de conta bancaria (LGPD).
     */
    MovimentacaoConsolidada consultarMovimentacao(String idExternoConsentimento, String correlationId);
}
