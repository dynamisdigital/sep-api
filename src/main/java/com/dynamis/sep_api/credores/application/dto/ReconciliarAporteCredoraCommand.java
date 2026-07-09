package com.dynamis.sep_api.credores.application.dto;

import com.dynamis.sep_api.credores.domain.vo.StatusAporteCredora;

/**
 * Comando de reconciliacao do aporte (Sprint 29 Task 29.5). {@code referenciaEscrow} e a referencia
 * interna devolvida no registro (Task 29.2); {@code resultado} deve ser terminal ({@code LIQUIDADO}
 * ou {@code FALHOU}); {@code motivoSanitizado} e obrigatorio na falha e nunca deve conter erro
 * bruto de provider. Validacao no use case com erros 400.
 */
public record ReconciliarAporteCredoraCommand(
        String referenciaEscrow, StatusAporteCredora resultado, String motivoSanitizado) {}
