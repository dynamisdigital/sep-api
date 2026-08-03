package com.dynamis.sep_api.identity.web.controller;

import com.dynamis.sep_api.identity.application.ClientChannel;
import com.dynamis.sep_api.identity.application.usecase.AutenticarUsuarioUseCase;
import com.dynamis.sep_api.identity.application.usecase.LogoutAllUseCase;
import com.dynamis.sep_api.identity.application.usecase.LogoutUseCase;
import com.dynamis.sep_api.identity.application.usecase.RefreshTokenUseCase;
import com.dynamis.sep_api.identity.infrastructure.security.LockoutProperties;
import com.dynamis.sep_api.identity.infrastructure.security.RateLimitFilter;
import com.dynamis.sep_api.identity.infrastructure.security.RefreshCookieService;
import com.dynamis.sep_api.identity.infrastructure.security.UsuarioAutenticado;
import com.dynamis.sep_api.identity.web.dto.LoginRequestDto;
import com.dynamis.sep_api.identity.web.dto.LogoutRequestDto;
import com.dynamis.sep_api.identity.web.dto.PoliticaLockoutResponseDto;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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
    private final RefreshCookieService refreshCookieService;
    private final LockoutProperties lockoutProperties;

    public AuthController(
            AutenticarUsuarioUseCase autenticarUsuarioUseCase,
            RefreshTokenUseCase refreshTokenUseCase,
            LogoutUseCase logoutUseCase,
            LogoutAllUseCase logoutAllUseCase,
            UsuarioRepository usuarioRepository,
            UsuarioMapper mapper,
            RefreshCookieService refreshCookieService,
            LockoutProperties lockoutProperties) {
        this.autenticarUsuarioUseCase = autenticarUsuarioUseCase;
        this.refreshTokenUseCase = refreshTokenUseCase;
        this.logoutUseCase = logoutUseCase;
        this.logoutAllUseCase = logoutAllUseCase;
        this.usuarioRepository = usuarioRepository;
        this.mapper = mapper;
        this.refreshCookieService = refreshCookieService;
        this.lockoutProperties = lockoutProperties;
    }

    @GetMapping("/politica-lockout")
    @Operation(
            summary = "Consultar politica de bloqueio de conta",
            description = "Somente leitura e sem autenticacao: quem precisa do valor esta bloqueado e, por definicao,"
                    + " nao tem sessao. Serve a pagina de conta bloqueada, que renderiza sem ter recebido resposta de"
                    + " API e por isso fixava a duracao no texto. Os mesmos numeros ja sao publicados pela message do"
                    + " 423.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Politica vigente",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = PoliticaLockoutResponseDto.class)))
    })
    public PoliticaLockoutResponseDto politicaLockout() {
        return PoliticaLockoutResponseDto.de(lockoutProperties);
    }

    @PostMapping("/login")
    @Operation(
            summary = "Autenticar usuario",
            description = "Senha valida emite access + refresh quando MFA nao esta ativo (refresh via cookie HttpOnly"
                    + " se X-Client-Channel=WEB, body caso contrario). Com MFA, retorna mfaChallengeId.")
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
                                schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "423",
                description = "Conta bloqueada por excesso de tentativas (5 falhas em 15 min; expira em 30 min)",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "429",
                description = "Rate limit por IP excedido",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<TokenResponseDto> login(
            @Valid @RequestBody LoginRequestDto dto,
            @RequestHeader(value = ClientChannel.HEADER, required = false) String canalHeader,
            HttpServletRequest request) {
        TokenResponseDto body = autenticarUsuarioUseCase.executar(
                dto, RateLimitFilter.extrairIp(request), request.getHeader("User-Agent"));
        return refreshCookieService.emitir(ClientChannel.fromHeader(canalHeader), body);
    }

    @PostMapping("/refresh")
    @Operation(
            summary = "Rotacionar refresh token",
            description = "Recebe refresh token cru via cookie HttpOnly (WEB) ou no body (MOBILE); marca como USADO e"
                    + " emite par novo (mesma familia).")
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
                description = "Refresh token invalido, expirado, revogado ou ausente",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<TokenResponseDto> refresh(
            @RequestBody(required = false) RefreshTokenRequestDto dto,
            @RequestHeader(value = ClientChannel.HEADER, required = false) String canalHeader,
            @CookieValue(name = "${app.refresh-cookie.name:sep-refresh}", required = false) String refreshCookie) {
        String refresh = (dto != null
                        && dto.refreshToken() != null
                        && !dto.refreshToken().isBlank())
                ? dto.refreshToken()
                : refreshCookie;
        if (refresh == null || refresh.isBlank()) {
            throw new BadCredentialsException("Refresh token ausente");
        }
        TokenResponseDto body = refreshTokenUseCase.executar(refresh);
        return refreshCookieService.emitir(ClientChannel.fromHeader(canalHeader), body);
    }

    @PostMapping("/logout")
    @Operation(
            summary = "Logout do dispositivo atual",
            description = "Revoga o refresh token (cookie WEB ou body MOBILE) e limpa o cookie no canal WEB.")
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
    public ResponseEntity<Void> logout(
            @RequestBody(required = false) LogoutRequestDto dto,
            @RequestHeader(value = ClientChannel.HEADER, required = false) String canalHeader,
            @CookieValue(name = "${app.refresh-cookie.name:sep-refresh}", required = false) String refreshCookie) {
        String refresh = (dto != null
                        && dto.refreshToken() != null
                        && !dto.refreshToken().isBlank())
                ? dto.refreshToken()
                : refreshCookie;
        if (refresh != null && !refresh.isBlank()) {
            logoutUseCase.executar(refresh);
        }
        if (ClientChannel.fromHeader(canalHeader) == ClientChannel.WEB) {
            return ResponseEntity.noContent()
                    .header(HttpHeaders.SET_COOKIE, refreshCookieService.construirCookieDeLimpeza())
                    .build();
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout-all")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Logout de todos os dispositivos",
            description = "Revoga todos os refresh tokens ativos do usuario autenticado; limpa cookie WEB se presente.")
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
    public ResponseEntity<Void> logoutAll(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @RequestHeader(value = ClientChannel.HEADER, required = false) String canalHeader) {
        logoutAllUseCase.executar(principal.id());
        if (ClientChannel.fromHeader(canalHeader) == ClientChannel.WEB) {
            return ResponseEntity.noContent()
                    .header(HttpHeaders.SET_COOKIE, refreshCookieService.construirCookieDeLimpeza())
                    .build();
        }
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
