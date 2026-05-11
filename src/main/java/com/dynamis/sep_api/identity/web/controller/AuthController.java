package com.dynamis.sep_api.identity.web.controller;

import com.dynamis.sep_api.identity.application.usecase.AutenticarUsuarioUseCase;
import com.dynamis.sep_api.identity.application.usecase.LogoutAllUseCase;
import com.dynamis.sep_api.identity.application.usecase.LogoutUseCase;
import com.dynamis.sep_api.identity.application.usecase.RefreshTokenUseCase;
import com.dynamis.sep_api.identity.infrastructure.security.RateLimitFilter;
import com.dynamis.sep_api.identity.infrastructure.security.UsuarioAutenticado;
import com.dynamis.sep_api.identity.web.dto.LoginRequestDto;
import com.dynamis.sep_api.identity.web.dto.LogoutRequestDto;
import com.dynamis.sep_api.identity.web.dto.RefreshTokenRequestDto;
import com.dynamis.sep_api.identity.web.dto.TokenResponseDto;
import com.dynamis.sep_api.shared.exception.ErrorResponseDto;
import com.dynamis.sep_api.usuarios.application.exception.UsuarioNaoEncontradoException;
import com.dynamis.sep_api.usuarios.domain.model.Usuario;
import com.dynamis.sep_api.usuarios.infrastructure.persistence.UsuarioRepository;
import com.dynamis.sep_api.usuarios.web.dto.UsuarioResponseDto;
import com.dynamis.sep_api.usuarios.web.mapper.UsuarioMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "auth", description = "Autenticacao, refresh, logout e usuario autenticado")
public class AuthController {

    private final AutenticarUsuarioUseCase autenticarUsuarioUseCase;
    private final RefreshTokenUseCase refreshTokenUseCase;
    private final LogoutUseCase logoutUseCase;
    private final LogoutAllUseCase logoutAllUseCase;
    private final UsuarioRepository usuarioRepository;
    private final UsuarioMapper mapper;

    public AuthController(
            AutenticarUsuarioUseCase autenticarUsuarioUseCase,
            RefreshTokenUseCase refreshTokenUseCase,
            LogoutUseCase logoutUseCase,
            LogoutAllUseCase logoutAllUseCase,
            UsuarioRepository usuarioRepository,
            UsuarioMapper mapper) {
        this.autenticarUsuarioUseCase = autenticarUsuarioUseCase;
        this.refreshTokenUseCase = refreshTokenUseCase;
        this.logoutUseCase = logoutUseCase;
        this.logoutAllUseCase = logoutAllUseCase;
        this.usuarioRepository = usuarioRepository;
        this.mapper = mapper;
    }

    @PostMapping("/login")
    @Operation(
            summary = "Autenticar usuario",
            description =
                    "Senha valida emite access + refresh quando MFA nao esta ativo; com MFA, retorna mfaChallengeId.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Sessao ou desafio MFA emitido",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = TokenResponseDto.class))),
        @ApiResponse(
                responseCode = "400",
                description = "Requisicao invalida",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "401",
                description = "Credenciais invalidas",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<TokenResponseDto> login(@Valid @RequestBody LoginRequestDto dto, HttpServletRequest request) {
        return ResponseEntity.ok(autenticarUsuarioUseCase.executar(
                dto, RateLimitFilter.extrairIp(request), request.getHeader("User-Agent")));
    }

    @PostMapping("/refresh")
    @Operation(
            summary = "Rotacionar refresh token",
            description = "Recebe refresh token cru, marca como USADO, emite par novo (mesma familia).")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Novo par emitido",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = TokenResponseDto.class))),
        @ApiResponse(
                responseCode = "401",
                description = "Refresh token invalido, expirado ou revogado",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<TokenResponseDto> refresh(@Valid @RequestBody RefreshTokenRequestDto dto) {
        return ResponseEntity.ok(refreshTokenUseCase.executar(dto.refreshToken()));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout do dispositivo atual", description = "Revoga o refresh token informado.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Refresh token revogado (idempotente)"),
        @ApiResponse(
                responseCode = "400",
                description = "Requisicao invalida",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequestDto dto) {
        logoutUseCase.executar(dto.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout-all")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Logout de todos os dispositivos",
            description = "Revoga todos os refresh tokens ativos do usuario autenticado.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Todos os refresh tokens do usuario revogados"),
        @ApiResponse(
                responseCode = "401",
                description = "Token ausente",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<Void> logoutAll(@AuthenticationPrincipal UsuarioAutenticado principal) {
        logoutAllUseCase.executar(principal.id());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Usuario autenticado", description = "Retorna o usuario do JWT atual.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Usuario autenticado",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = UsuarioResponseDto.class))),
        @ApiResponse(
                responseCode = "401",
                description = "Token ausente ou invalido",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<UsuarioResponseDto> me(@AuthenticationPrincipal UsuarioAutenticado principal) {
        Usuario usuario = usuarioRepository
                .findById(principal.id())
                .orElseThrow(() -> new UsuarioNaoEncontradoException(principal.id()));
        return ResponseEntity.ok(mapper.toResponse(usuario));
    }
}
