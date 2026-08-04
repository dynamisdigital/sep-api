package com.dynamis.sep_api.identity.web.controller;

import com.dynamis.sep_api.identity.application.ClientChannel;
import com.dynamis.sep_api.identity.application.service.LockoutService;
import com.dynamis.sep_api.identity.application.usecase.AutenticarUsuarioUseCase;
import com.dynamis.sep_api.identity.application.usecase.LogoutAllUseCase;
import com.dynamis.sep_api.identity.application.usecase.LogoutUseCase;
import com.dynamis.sep_api.identity.application.usecase.RefreshTokenUseCase;
import com.dynamis.sep_api.identity.infrastructure.security.JwtAuthenticationFilter;
import com.dynamis.sep_api.identity.infrastructure.security.JwtTokenProvider;
import com.dynamis.sep_api.identity.infrastructure.security.RefreshCookieService;
import com.dynamis.sep_api.identity.infrastructure.security.UsuarioAutenticado;
import com.dynamis.sep_api.identity.web.dto.LoginRequestDto;
import com.dynamis.sep_api.identity.web.dto.LogoutRequestDto;
import com.dynamis.sep_api.identity.web.dto.RefreshTokenRequestDto;
import com.dynamis.sep_api.identity.web.dto.TokenResponseDto;
import com.dynamis.sep_api.shared.exception.ApiExceptionHandler;
import com.dynamis.sep_api.usuarios.domain.model.Role;
import com.dynamis.sep_api.usuarios.domain.model.Usuario;
import com.dynamis.sep_api.usuarios.infrastructure.persistence.UsuarioRepository;
import com.dynamis.sep_api.usuarios.web.dto.UsuarioResponseDto;
import com.dynamis.sep_api.usuarios.web.mapper.UsuarioMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ApiExceptionHandler.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AutenticarUsuarioUseCase autenticarUsuarioUseCase;

    @MockBean
    private RefreshTokenUseCase refreshTokenUseCase;

    @MockBean
    private LogoutUseCase logoutUseCase;

    @MockBean
    private LogoutAllUseCase logoutAllUseCase;

    @MockBean
    private UsuarioRepository usuarioRepository;

    @MockBean
    private UsuarioMapper mapper;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private RefreshCookieService refreshCookieService;

    @MockBean
    private LockoutService lockoutService;

    @BeforeEach
    void configureCookieService() {
        // Default: comportamento MOBILE/sem canal — devolve o body como esta.
        when(refreshCookieService.emitir(any(), any())).thenAnswer(invocation -> {
            TokenResponseDto body = invocation.getArgument(1);
            return ResponseEntity.ok(body);
        });
        when(refreshCookieService.construirCookieDeLimpeza())
                .thenReturn("sep-refresh=; Max-Age=0; Path=/api/v1/auth; HttpOnly; SameSite=Lax");
    }

    @AfterEach
    void clean() {
        SecurityContextHolder.clearContext();
    }

    private UsuarioResponseDto usuarioDto() {
        return new UsuarioResponseDto(
                UUID.randomUUID(),
                "admin@sep.test",
                Role.ADMIN,
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                "system",
                "system",
                false,
                false);
    }

    // /politica-lockout nao tem caso aqui de proposito: esta slice roda com addFilters = false,
    // entao nao prova acesso publico, e assertar os defaults 5/15/30 aceitaria exatamente a
    // implementacao de constantes fixas que a PoliticaLockoutIT existe para rejeitar. A IT cobre o
    // endpoint inteiro (Sprint 34 Task 34.5).

    @Test
    void postLoginRetorna200ComAccessERefresh() throws Exception {
        UsuarioResponseDto usuario = usuarioDto();
        when(autenticarUsuarioUseCase.executar(any(), any(), any()))
                .thenReturn(TokenResponseDto.comTokens("token-jwt", 900L, "refresh-cru", usuario));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequestDto("admin@sep.test", "123456"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("token-jwt"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-cru"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.mfaRequired").value(false))
                .andExpect(jsonPath("$.mfaChallengeId").doesNotExist())
                .andExpect(jsonPath("$.usuario.password").doesNotExist());

        verify(refreshCookieService).emitir(eq(ClientChannel.MOBILE), any());
    }

    @Test
    void postLoginWebEmiteCookieEOmiteRefreshDoBody() throws Exception {
        // 5F-FIX-02: WEB recebe Set-Cookie HttpOnly e refreshToken=null no body.
        UsuarioResponseDto usuario = usuarioDto();
        when(autenticarUsuarioUseCase.executar(any(), any(), any()))
                .thenReturn(TokenResponseDto.comTokens("token-jwt", 900L, "refresh-cru", usuario));
        when(refreshCookieService.emitir(eq(ClientChannel.WEB), any())).thenAnswer(invocation -> {
            TokenResponseDto body = invocation.getArgument(1);
            TokenResponseDto bodySemRefresh = new TokenResponseDto(
                    body.accessToken(),
                    body.tokenType(),
                    body.expiresIn(),
                    null,
                    body.usuario(),
                    body.mfaRequired(),
                    body.mfaChallengeId());
            return ResponseEntity.ok()
                    .header(
                            HttpHeaders.SET_COOKIE,
                            "sep-refresh=refresh-cru; Max-Age=2592000; Path=/api/v1/auth; HttpOnly;" + " SameSite=Lax")
                    .body(bodySemRefresh);
        });

        mockMvc.perform(post("/api/v1/auth/login")
                        .header(ClientChannel.HEADER, "WEB")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequestDto("admin@sep.test", "123456"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("token-jwt"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("sep-refresh=refresh-cru")));

        verify(refreshCookieService).emitir(eq(ClientChannel.WEB), any());
    }

    @Test
    void postLoginComMfaRetornaApenasChallengeId() throws Exception {
        UUID challengeId = UUID.randomUUID();
        when(autenticarUsuarioUseCase.executar(any(), any(), any()))
                .thenReturn(TokenResponseDto.desafioMfa(challengeId));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequestDto("u@sep.test", "senha"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mfaRequired").value(true))
                .andExpect(jsonPath("$.mfaChallengeId").value(challengeId.toString()))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test
    void postLoginCredenciaisInvalidasRetorna401() throws Exception {
        when(autenticarUsuarioUseCase.executar(any(), any(), any()))
                .thenThrow(new BadCredentialsException("Credenciais invalidas"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequestDto("admin@sep.test", "errada"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void postRefreshRotacionaTokenComBody() throws Exception {
        UsuarioResponseDto usuario = usuarioDto();
        when(refreshTokenUseCase.executar("cru-antigo"))
                .thenReturn(TokenResponseDto.comTokens("novo-access", 900L, "novo-refresh", usuario));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequestDto("cru-antigo"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("novo-access"))
                .andExpect(jsonPath("$.refreshToken").value("novo-refresh"));
    }

    @Test
    void postRefreshWebUsaCookieQuandoBodyAusente() throws Exception {
        // 5F-FIX-02: refresh sem body deve ler do cookie sep-refresh
        UsuarioResponseDto usuario = usuarioDto();
        when(refreshTokenUseCase.executar("cookie-refresh"))
                .thenReturn(TokenResponseDto.comTokens("novo-access", 900L, "novo-refresh", usuario));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .header(ClientChannel.HEADER, "WEB")
                        .cookie(new Cookie("sep-refresh", "cookie-refresh")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("novo-access"));

        verify(refreshTokenUseCase).executar("cookie-refresh");
    }

    @Test
    void postRefreshSemBodyNemCookieRetorna401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")).andExpect(status().isUnauthorized());
    }

    @Test
    void postRefreshTokenInvalidoRetorna401() throws Exception {
        when(refreshTokenUseCase.executar(any())).thenThrow(new BadCredentialsException("Refresh token invalido"));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequestDto("invalido"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void postLogoutRevogaRefreshTokenDoBody() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LogoutRequestDto("refresh-cru"))))
                .andExpect(status().isNoContent());

        verify(logoutUseCase).executar("refresh-cru");
    }

    @Test
    void postLogoutWebLimpaCookieEUsaCookieParaRevogar() throws Exception {
        // 5F-FIX-02: WEB envia cookie; backend revoga via cookie e devolve Set-Cookie com Max-Age=0.
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(ClientChannel.HEADER, "WEB")
                        .cookie(new Cookie("sep-refresh", "cookie-cru")))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("sep-refresh", 0));

        verify(logoutUseCase).executar("cookie-cru");
    }

    @Test
    void postLogoutSemBodyNemCookieEhIdempotente() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")).andExpect(status().isNoContent());
        // logoutUseCase nao deve ser chamado com null/empty
    }

    @Test
    @WithMockUser
    void postLogoutAllRevogaTodosTokens() throws Exception {
        UUID id = UUID.randomUUID();
        UsuarioAutenticado principal = new UsuarioAutenticado(id, "u@sep.test", Role.CLIENTE);
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        when(logoutAllUseCase.executar(id)).thenReturn(3);

        mockMvc.perform(post("/api/v1/auth/logout-all")).andExpect(status().isNoContent());

        verify(logoutAllUseCase).executar(eq(id));
    }

    @Test
    @WithMockUser
    void postLogoutAllWebLimpaCookie() throws Exception {
        UUID id = UUID.randomUUID();
        UsuarioAutenticado principal = new UsuarioAutenticado(id, "u@sep.test", Role.CLIENTE);
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        when(logoutAllUseCase.executar(id)).thenReturn(3);

        mockMvc.perform(post("/api/v1/auth/logout-all").header(ClientChannel.HEADER, "WEB"))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("sep-refresh", 0));
    }

    @Test
    @WithMockUser
    void getMeRetornaUsuarioDoPrincipal() throws Exception {
        UUID id = UUID.randomUUID();
        UsuarioAutenticado principal = new UsuarioAutenticado(id, "admin@sep.test", Role.ADMIN);
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        Usuario usuario = Usuario.criar("admin@sep.test", "$2a$hash", Role.ADMIN);
        UsuarioResponseDto response = new UsuarioResponseDto(
                id,
                "admin@sep.test",
                Role.ADMIN,
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                "system",
                "system",
                false,
                false);
        when(usuarioRepository.findById(id)).thenReturn(Optional.of(usuario));
        when(mapper.toResponse(usuario)).thenReturn(response);

        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.username").value("admin@sep.test"));
    }
}
