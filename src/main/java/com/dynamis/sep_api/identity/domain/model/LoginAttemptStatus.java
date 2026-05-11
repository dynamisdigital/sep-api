package com.dynamis.sep_api.identity.domain.model;

/**
 * Resultado de cada tentativa de login (Sprint 5 Task 5.4).
 *
 * <p>Usado pelo {@code LockoutService} para contar falhas em janela deslizante e pelo audit log de
 * seguranca.
 */
public enum LoginAttemptStatus {
    SUCESSO,
    SENHA_INVALIDA,
    USUARIO_INEXISTENTE,
    CONTA_BLOQUEADA,
    TOTP_INVALIDO,
    TOTP_NECESSARIO
}
