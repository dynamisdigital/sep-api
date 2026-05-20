package com.dynamis.sep_api.contratos.application.usecase.command;

import com.dynamis.sep_api.shared.exception.ValidacaoException;

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
 *
 * <p>Validacao falhou -> {@link ValidacaoException} (HTTP 400, codigo
 * {@value #CODIGO_JUSTIFICATIVA_INVALIDA}) — garante mapeamento via {@code ApiExceptionHandler}
 * mesmo quando o comando e instanciado fora do controller (ex.: codigo interno/listener).
 */
public record CancelarContratoCommand(UUID contratoId, UUID canceladoPorId, String justificativa) {

    public static final int JUSTIFICATIVA_MIN = 10;
    public static final int JUSTIFICATIVA_MAX = 500;
    public static final String CODIGO_JUSTIFICATIVA_INVALIDA = "CTR-400-001";

    public CancelarContratoCommand {
        Objects.requireNonNull(contratoId, "contratoId obrigatoria");
        Objects.requireNonNull(canceladoPorId, "canceladoPorId obrigatorio");
        Objects.requireNonNull(justificativa, "justificativa obrigatoria");
        String trim = justificativa.trim();
        if (trim.length() < JUSTIFICATIVA_MIN || trim.length() > JUSTIFICATIVA_MAX) {
            throw new ValidacaoException(
                    CODIGO_JUSTIFICATIVA_INVALIDA,
                    "justificativa deve ter entre " + JUSTIFICATIVA_MIN + " e " + JUSTIFICATIVA_MAX
                            + " caracteres (apos trim)");
        }
        justificativa = trim;
    }
}
