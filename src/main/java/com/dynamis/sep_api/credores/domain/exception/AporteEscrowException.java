package com.dynamis.sep_api.credores.domain.exception;

/**
 * Falha sanitizada no registro do aporte no escrow (Sprint 29 Task 29.2). Mensagem fixa: o erro
 * bruto do componente de escrow/provider fica somente na causa (log interno) e nunca em resposta
 * publica.
 */
public class AporteEscrowException extends RuntimeException {

    public static final String MOTIVO_SANITIZADO = "Falha ao registrar aporte no escrow";

    public AporteEscrowException(Throwable causa) {
        super(MOTIVO_SANITIZADO, causa);
    }
}
