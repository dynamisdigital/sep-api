package com.dynamis.sep_api.contratos.application.usecase.command;

import java.util.Objects;
import java.util.UUID;

/**
 * Comando para registrar aceite do tomador sobre a versao vigente do contrato (Sprint 10 Task
 * 10.4).
 *
 * <p>{@code ipOrigem} e {@code userAgentOrigem} sao evidencias tecnicas extraidas do
 * {@code HttpServletRequest} pela borda web; o use case persiste como vieram (com truncamento
 * aplicado pela entidade {@code AceiteContrato}). {@code null} aceito pra ambos quando o
 * mecanismo de extracao nao identificar IP/UA confiavel.
 *
 * <p>Step-up authentication (header {@code X-Step-Up-Token}) e validada na borda web via
 * {@code @RequireStepUp}; o use case nao re-valida.
 */
public record RegistrarAceiteCommand(
        UUID contratoId, UUID tomadorAutenticadoId, String ipOrigem, String userAgentOrigem) {

    public RegistrarAceiteCommand {
        Objects.requireNonNull(contratoId, "contratoId obrigatoria");
        Objects.requireNonNull(tomadorAutenticadoId, "tomadorAutenticadoId obrigatorio");
    }
}
