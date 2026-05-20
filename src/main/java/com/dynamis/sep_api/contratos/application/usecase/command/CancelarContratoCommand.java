package com.dynamis.sep_api.contratos.application.usecase.command;

import java.util.Objects;
import java.util.UUID;

/**
 * Comando para cancelar contrato antes do aceite (Sprint 10 Task 10.5).
 *
 * <p>{@code canceladoPorId} e o usuario autenticado da operacao (FINANCEIRO ou ADMIN — validado na
 * borda web via {@code @PreAuthorize}). {@code justificativa} e obrigatoria e auditavel (Task
 * 10.7): minimo {@value #JUSTIFICATIVA_MIN} e maximo {@value #JUSTIFICATIVA_MAX} caracteres apos
 * trim.
 *
 * <p>Step-up authentication e responsabilidade da borda web ({@code @RequireStepUp}).
 */
public record CancelarContratoCommand(UUID contratoId, UUID canceladoPorId, String justificativa) {

    public static final int JUSTIFICATIVA_MIN = 10;
    public static final int JUSTIFICATIVA_MAX = 500;

    public CancelarContratoCommand {
        Objects.requireNonNull(contratoId, "contratoId obrigatoria");
        Objects.requireNonNull(canceladoPorId, "canceladoPorId obrigatorio");
        Objects.requireNonNull(justificativa, "justificativa obrigatoria");
        String trim = justificativa.trim();
        if (trim.length() < JUSTIFICATIVA_MIN || trim.length() > JUSTIFICATIVA_MAX) {
            throw new IllegalArgumentException("justificativa deve ter entre " + JUSTIFICATIVA_MIN + " e "
                    + JUSTIFICATIVA_MAX + " chars (apos trim)");
        }
        justificativa = trim;
    }
}
