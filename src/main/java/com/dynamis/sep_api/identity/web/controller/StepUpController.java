package com.dynamis.sep_api.identity.web.controller;

import com.dynamis.sep_api.identity.application.usecase.CompletarStepUpUseCase;
import com.dynamis.sep_api.identity.application.usecase.IniciarStepUpUseCase;
import com.dynamis.sep_api.identity.infrastructure.security.UsuarioAutenticado;
import com.dynamis.sep_api.identity.web.dto.StepUpCompleteRequestDto;
import com.dynamis.sep_api.identity.web.dto.StepUpCompleteResponseDto;
import com.dynamis.sep_api.identity.web.dto.StepUpInitiateResponseDto;
import com.dynamis.sep_api.shared.exception.ErrorResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/step-up")
@Tag(name = "step-up", description = "Step-up authentication para operacoes sensiveis (Sprint 5).")
public class StepUpController {

    private final IniciarStepUpUseCase iniciar;
    private final CompletarStepUpUseCase completar;

    public StepUpController(IniciarStepUpUseCase iniciar, CompletarStepUpUseCase completar) {
        this.iniciar = iniciar;
        this.completar = completar;
    }

    @PostMapping("/initiate")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Iniciar step-up", description = "Emite challenge para reautenticacao TOTP.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Challenge emitido",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = StepUpInitiateResponseDto.class))),
        @ApiResponse(
                responseCode = "400",
                description = "MFA nao habilitado",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<StepUpInitiateResponseDto> initiate(@AuthenticationPrincipal UsuarioAutenticado principal) {
        return ResponseEntity.ok(new StepUpInitiateResponseDto(iniciar.executar(principal.id())));
    }

    @PostMapping("/complete")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Concluir step-up",
            description = "Recebe codigo TOTP/backup-code e emite step-up token (5 min).")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Step-up token emitido",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = StepUpCompleteResponseDto.class))),
        @ApiResponse(
                responseCode = "400",
                description = "Codigo invalido ou challenge expirado",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "403",
                description = "Challenge nao pertence ao usuario autenticado",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<StepUpCompleteResponseDto> complete(
            @AuthenticationPrincipal UsuarioAutenticado principal, @Valid @RequestBody StepUpCompleteRequestDto dto) {
        String token = completar.executar(dto.stepUpChallengeId(), dto.codigo(), principal.id());
        return ResponseEntity.ok(new StepUpCompleteResponseDto(token));
    }
}
