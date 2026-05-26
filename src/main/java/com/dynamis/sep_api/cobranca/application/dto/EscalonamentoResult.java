package com.dynamis.sep_api.cobranca.application.dto;

/**
 * Resultado do {@code EscalarCobrancaUseCase} (Sprint 13 Task 13.4).
 *
 * <p>Carrega flags operacionais da etapa pro caller orquestrar — listener/job decide se publica
 * evento de inadimplente, sinaliza backoffice ou ativa contato manual. Use case nao executa
 * essas transicoes diretamente.
 */
public record EscalonamentoResult(
        boolean tinhaEtapa,
        boolean flagContatoManual,
        boolean escalonarBackoffice,
        boolean marcarInadimplente,
        int eventosCriados) {

    public static EscalonamentoResult semEtapa() {
        return new EscalonamentoResult(false, false, false, false, 0);
    }
}
