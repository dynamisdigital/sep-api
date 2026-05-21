package com.dynamis.sep_api.contratos.web.dto;

import com.dynamis.sep_api.contratos.application.usecase.command.CancelarContratoCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Justificativa para cancelamento pre-aceite (auditavel)")
public record CancelarContratoRequest(
        @NotBlank
                @Size(min = CancelarContratoCommand.JUSTIFICATIVA_MIN, max = CancelarContratoCommand.JUSTIFICATIVA_MAX)
                @Schema(
                        description = "Motivo livre (10 a 500 chars apos trim)",
                        example = "Contrato cancelado por divergencia operacional antes do aceite.")
                String justificativa) {}
