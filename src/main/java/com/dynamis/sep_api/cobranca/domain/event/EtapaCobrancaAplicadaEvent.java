package com.dynamis.sep_api.cobranca.domain.event;

import java.util.UUID;

/**
 * Disparado apos {@code EscalarCobrancaUseCase} aplicar uma etapa do workflow (Sprint 13 Task
 * 13.4). Carrega flags operacionais pro caller assincrono observar:
 *
 * <ul>
 *   <li>{@code flagContatoManual}: backoffice precisa entrar em contato direto (Sprint 14).
 *   <li>{@code escalonarBackoffice}: caso passou pra fila operacional (Sprint 14).
 *   <li>{@code marcarInadimplente}: parcela atingiu 90+ dias — {@code
 *       MarcarParcelaInadimplenteJob} (Task 13.5) realiza a transicao de status.
 * </ul>
 *
 * <p>O use case nao executa as transicoes diretamente — apenas publica o evento. Listeners
 * dedicados orquestram cada flag respeitando boundaries transacionais (REQUIRES_NEW).
 */
public record EtapaCobrancaAplicadaEvent(
        UUID parcelaId,
        int diasAtraso,
        boolean flagContatoManual,
        boolean escalonarBackoffice,
        boolean marcarInadimplente,
        int eventosCriados) {

    public boolean temFlagOperacional() {
        return flagContatoManual || escalonarBackoffice || marcarInadimplente;
    }
}
