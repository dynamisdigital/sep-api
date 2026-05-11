package com.dynamis.sep_api.identity.web.controller;

import com.dynamis.sep_api.identity.application.usecase.ConfirmarTotpUseCase;
import com.dynamis.sep_api.identity.application.usecase.DesabilitarTotpUseCase;
import com.dynamis.sep_api.identity.application.usecase.HabilitarTotpUseCase;
import com.dynamis.sep_api.identity.application.usecase.VerificarTotpUseCase;
import com.dynamis.sep_api.identity.infrastructure.security.UsuarioAutenticado;
import com.dynamis.sep_api.identity.web.dto.TotpConfirmRequestDto;
import com.dynamis.sep_api.identity.web.dto.TotpDisableRequestDto;
import com.dynamis.sep_api.identity.web.dto.TotpSetupResponseDto;
import com.dynamis.sep_api.identity.web.dto.TotpVerifyRequestDto;
import com.dynamis.sep_api.identity.web.dto.TotpVerifyResponseDto;
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
@RequestMapping("/api/v1/auth/totp")
@Tag(name = "mfa", description = "MFA TOTP: setup, confirm, verify e disable.")
public class MfaController {

    private final HabilitarTotpUseCase habilitar;
    private final ConfirmarTotpUseCase confirmar;
    private final VerificarTotpUseCase verificar;
    private final DesabilitarTotpUseCase desabilitar;

    public MfaController(
            HabilitarTotpUseCase habilitar,
            ConfirmarTotpUseCase confirmar,
            VerificarTotpUseCase verificar,
            DesabilitarTotpUseCase desabilitar) {
        this.habilitar = habilitar;
        this.confirmar = confirmar;
        this.verificar = verificar;
        this.desabilitar = desabilitar;
    }

    @PostMapping("/setup")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Iniciar setup TOTP",
            description = "Gera secret, QR code e 10 backup codes (exibidos uma unica vez).")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Setup iniciado",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = TotpSetupResponseDto.class))),
        @ApiResponse(
                responseCode = "401",
                description = "Token ausente",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "409",
                description = "MFA ja habilitado",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<TotpSetupResponseDto> setup(@AuthenticationPrincipal UsuarioAutenticado principal) {
        return ResponseEntity.ok(habilitar.executar(principal.id()));
    }

    @PostMapping("/confirm")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Confirmar primeiro codigo TOTP",
            description = "Recebe o primeiro codigo gerado pelo app e marca o MFA como ATIVO.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "MFA ativado"),
        @ApiResponse(
                responseCode = "400",
                description = "Codigo invalido ou setup nao iniciado",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "401",
                description = "Token ausente",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "409",
                description = "MFA ja habilitado",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<Void> confirm(
            @AuthenticationPrincipal UsuarioAutenticado principal, @Valid @RequestBody TotpConfirmRequestDto dto) {
        confirmar.executar(principal.id(), dto.codigo());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/verify")
    @Operation(
            summary = "Verificar TOTP durante login",
            description = "Publico; usado apos validar senha. Aceita codigo TOTP de 6 digitos ou backup"
                    + " code. Wiring com mfaChallengeId entra na Task 5.3.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Codigo aceito",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = TotpVerifyResponseDto.class))),
        @ApiResponse(
                responseCode = "400",
                description = "Codigo invalido ou MFA nao habilitado",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<TotpVerifyResponseDto> verify(@Valid @RequestBody TotpVerifyRequestDto dto) {
        return ResponseEntity.ok(verificar.executar(dto.usuarioId(), dto.codigo()));
    }

    @PostMapping("/disable")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Desabilitar TOTP", description = "Exige senha atual. Step-up token sera exigido na Task 5.6.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "MFA desabilitado"),
        @ApiResponse(
                responseCode = "400",
                description = "Senha incorreta ou MFA nao habilitado",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "401",
                description = "Token ausente",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<Void> disable(
            @AuthenticationPrincipal UsuarioAutenticado principal, @Valid @RequestBody TotpDisableRequestDto dto) {
        desabilitar.executar(principal.id(), dto.passwordAtual());
        return ResponseEntity.noContent().build();
    }
}
