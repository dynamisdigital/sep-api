package com.dynamis.sep_api.pix.web.controller;

import com.dynamis.sep_api.identity.infrastructure.security.UsuarioAutenticado;
import com.dynamis.sep_api.pix.application.usecase.ConsultarDesembolsoTomadorUseCase;
import com.dynamis.sep_api.pix.web.dto.PixDesembolsoTomadorResponse;
import com.dynamis.sep_api.shared.exception.ErrorResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Leituras Pix owner-scoped do tomador (Sprint 26 — Gates P1/P2). O tomador (ROLE_CLIENTE) enxerga
 * apenas um recorte publico minimo do estado Pix dos proprios contratos/parcelas, sem os campos e
 * comandos operacionais expostos aos perfis FINANCEIRO/ADMIN/BACKOFFICE. A ownership e validada no
 * use case; recurso inexistente e recurso alheio retornam o mesmo 404 neutro, sem identificador.
 */
@RestController
@RequestMapping("/api/v1/pix")
@Tag(name = "pix-tomador", description = "Leituras Pix owner-scoped do tomador (Sprint 26).")
public class PixTomadorController {

    private final ConsultarDesembolsoTomadorUseCase consultarDesembolsoTomadorUseCase;

    public PixTomadorController(ConsultarDesembolsoTomadorUseCase consultarDesembolsoTomadorUseCase) {
        this.consultarDesembolsoTomadorUseCase = consultarDesembolsoTomadorUseCase;
    }

    @GetMapping("/contratos/{contratoId}/desembolso")
    @PreAuthorize("hasRole('CLIENTE')")
    @Operation(
            summary = "Status de desembolso Pix de um contrato proprio",
            description = "Tomador consulta o status publico do desembolso Pix de um contrato proprio."
                    + " Read-only, sem step-up. Contrato inexistente, de outro tomador ou sem desembolso Pix"
                    + " retorna 404 neutro, sem identificador, para nao permitir enumeracao. Nao expoe chave"
                    + " Pix, txid, IDs internos, provider ou escrow. Exclusivo de ROLE_CLIENTE.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Status publico do desembolso Pix"),
        @ApiResponse(
                responseCode = "400",
                description = "contratoId invalido",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "401",
                description = "Token ausente ou invalido",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "403",
                description = "Sem role CLIENTE",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "404",
                description = "Contrato inexistente, de outro tomador ou sem desembolso Pix",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<PixDesembolsoTomadorResponse> consultarDesembolsoDoContrato(
            @PathVariable UUID contratoId, @AuthenticationPrincipal UsuarioAutenticado principal) {
        var resultado = consultarDesembolsoTomadorUseCase.executar(contratoId, principal.id());
        return ResponseEntity.ok(PixDesembolsoTomadorResponse.from(resultado));
    }
}
