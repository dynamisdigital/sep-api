package com.dynamis.sep_api.notificacao.application.port.out;

import com.dynamis.sep_api.notificacao.domain.model.Notificacao;

/** Persistencia do historico de notificacoes (Sprint 38, ADR 0021). */
public interface NotificacaoPort {

    /**
     * Grava a notificacao em transacao propria, que comita independentemente de quem chama.
     *
     * @return {@code false} quando a mesma origem ja tem notificacao nao falha para o mesmo usuario
     *     (ADR 0021 §4); qualquer outra violacao de integridade propaga.
     */
    boolean registrarSeInedita(Notificacao notificacao);

    /** Grava, em transacao propria, a entrega de uma notificacao ja registrada. */
    void atualizarEntrega(Notificacao notificacao);
}
