package com.dynamis.sep_api.credito.application.service;

import com.dynamis.sep_api.credito.application.service.dto.ContextoAvaliacaoCredito;
import com.dynamis.sep_api.credito.application.service.dto.RegraResultado;

/**
 * Interface de uma regra de credito do motor de Sprint 8 (ADR 0011 — motor Java puro). Cada
 * implementacao e um {@code @Component} agregado pelo {@code MotorRegrasCredito} via injecao de
 * lista.
 *
 * <p>Regras nao devem acessar repositorios diretamente — o contexto necessario e montado pelo use
 * case e passado em {@link ContextoAvaliacaoCredito}.
 */
public interface RegraCredito {

    /** Nome curto/estavel da regra — usado pra trilha auditavel e logs. */
    String nome();

    /**
     * Avalia a regra contra o contexto. Retorna {@link RegraResultado#passou(String)} quando regra
     * e atendida, {@code falhou(...)} ou {@code falhouBloqueante(...)} quando violada e {@code
     * pendente(...)} quando dado necessario nao esta presente.
     */
    RegraResultado avaliar(ContextoAvaliacaoCredito contexto);
}
