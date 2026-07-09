package com.dynamis.sep_api.credores.domain.exception;

import com.dynamis.sep_api.shared.exception.ConflitoException;

/**
 * Resultado de reconciliacao conflita com estado terminal ja consolidado do aporte (HTTP 409,
 * Sprint 29 Task 29.5) — ex.: FALHOU apos LIQUIDADO. Rejeicao explicita, sem alterar estado e sem
 * expor dado de escrow/provider.
 */
public class AporteReconciliacaoConflitanteException extends ConflitoException {

    public static final String CODIGO = "CRD-409-006";

    public AporteReconciliacaoConflitanteException() {
        super(CODIGO, "Reconciliacao conflita com o estado terminal do aporte");
    }
}
