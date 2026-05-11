package com.dynamis.sep_api.identity.application.port.out;

/**
 * Porta de saida (Sprint 5 Task 5.5) para consultar se uma senha aparece em vazamentos publicos.
 *
 * <p>Adapter padrao em dev-local pode retornar sempre {@code false}; o {@code
 * HaveIBeenPwnedClient} fica disponivel para ativacao via configuracao.
 */
public interface PasswordBreachChecker {

    boolean foiVazada(String senhaClara);
}
