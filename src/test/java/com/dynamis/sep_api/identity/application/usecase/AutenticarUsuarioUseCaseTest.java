package com.dynamis.sep_api.identity.application.usecase;

import com.dynamis.sep_api.identity.application.service.MfaChallengeService;
import com.dynamis.sep_api.identity.application.service.RefreshTokenService;
import com.dynamis.sep_api.identity.application.service.RefreshTokenService.TokenCru;
import com.dynamis.sep_api.identity.domain.model.RefreshToken;
import com.dynamis.sep_api.identity.domain.model.UsuarioTotpSecret;
import com.dynamis.sep_api.identity.infrastructure.persistence.UsuarioTotpSecretRepository;
import com.dynamis.sep_api.identity.infrastructure.security.JwtProperties;
import com.dynamis.sep_api.identity.infrastructure.security.JwtTokenProvider;
import com.dynamis.sep_api.identity.web.dto.LoginRequestDto;
import com.dynamis.sep_api.identity.web.dto.TokenResponseDto;
import com.dynamis.sep_api.usuarios.domain.model.Role;
import com.dynamis.sep_api.usuarios.domain.model.Usuario;
import com.dynamis.sep_api.usuarios.infrastructure.persistence.UsuarioRepository;
import com.dynamis.sep_api.usuarios.web.dto.UsuarioResponseDto;
import com.dynamis.sep_api.usuarios.web.mapper.UsuarioMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutenticarUsuarioUseCaseTest {

    @Mock
    private UsuarioRepository repository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private JwtProperties jwtProperties;

    @Mock
    private UsuarioMapper mapper;

    @Mock
    private UsuarioTotpSecretRepository totpRepository;

    @Mock
    private MfaChallengeService mfaChallengeService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private AutenticarUsuarioUseCase useCase;

    @Test
    void loginValidoSemMfaEmiteAccessERefresh() {
        Usuario usuario = Usuario.criar("admin@sep.test", "$2a$hash", Role.ADMIN);
        UsuarioResponseDto response = new UsuarioResponseDto(
                usuario.getId(),
                "admin@sep.test",
                Role.ADMIN,
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                "system",
                "system");
        when(repository.findByUsername("admin@sep.test")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("123456", "$2a$hash")).thenReturn(true);
        when(totpRepository.findByUsuarioId(usuario.getId())).thenReturn(Optional.empty());
        when(tokenProvider.gerarToken(usuario)).thenReturn("token-jwt-fake");
        when(jwtProperties.getAccessExpirationSeconds()).thenReturn(900L);
        RefreshToken persistido = RefreshToken.emitirNovoLogin(
                usuario.getId(), "hash-fake", OffsetDateTime.now().plusDays(30));
        when(refreshTokenService.emitirParaNovoLogin(usuario.getId()))
                .thenReturn(new TokenCru("refresh-cru", persistido));
        when(mapper.toResponse(usuario)).thenReturn(response);

        TokenResponseDto resultado = useCase.executar(new LoginRequestDto("admin@sep.test", "123456"));

        assertThat(resultado.accessToken()).isEqualTo("token-jwt-fake");
        assertThat(resultado.refreshToken()).isEqualTo("refresh-cru");
        assertThat(resultado.tokenType()).isEqualTo("Bearer");
        assertThat(resultado.expiresIn()).isEqualTo(900L);
        assertThat(resultado.mfaRequired()).isFalse();
        assertThat(resultado.mfaChallengeId()).isNull();
        assertThat(resultado.usuario()).isEqualTo(response);
    }

    @Test
    void loginValidoComMfaAtivoEmiteApenasChallenge() {
        Usuario usuario = Usuario.criar("u@sep.test", "$2a$hash", Role.CLIENTE);
        when(repository.findByUsername("u@sep.test")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("senha", "$2a$hash")).thenReturn(true);
        UsuarioTotpSecret ativo = UsuarioTotpSecret.iniciar(usuario.getId(), "cifrado");
        ativo.ativar();
        when(totpRepository.findByUsuarioId(usuario.getId())).thenReturn(Optional.of(ativo));
        UUID challengeId = UUID.randomUUID();
        when(mfaChallengeService.iniciar(usuario.getId())).thenReturn(challengeId);

        TokenResponseDto resultado = useCase.executar(new LoginRequestDto("u@sep.test", "senha"));

        assertThat(resultado.mfaRequired()).isTrue();
        assertThat(resultado.mfaChallengeId()).isEqualTo(challengeId);
        assertThat(resultado.accessToken()).isNull();
        assertThat(resultado.refreshToken()).isNull();
        verify(tokenProvider, never()).gerarToken(any());
        verify(refreshTokenService, never()).emitirParaNovoLogin(any());
    }

    @Test
    void mfaPendenteNaoTrataComoAtivo() {
        Usuario usuario = Usuario.criar("u@sep.test", "$2a$hash", Role.CLIENTE);
        UsuarioResponseDto response = new UsuarioResponseDto(
                usuario.getId(),
                "u@sep.test",
                Role.CLIENTE,
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                "system",
                "system");
        when(repository.findByUsername("u@sep.test")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("senha", "$2a$hash")).thenReturn(true);
        UsuarioTotpSecret pendente = UsuarioTotpSecret.iniciar(usuario.getId(), "cifrado");
        when(totpRepository.findByUsuarioId(usuario.getId())).thenReturn(Optional.of(pendente));
        when(tokenProvider.gerarToken(usuario)).thenReturn("jwt");
        when(jwtProperties.getAccessExpirationSeconds()).thenReturn(900L);
        when(refreshTokenService.emitirParaNovoLogin(usuario.getId()))
                .thenReturn(new TokenCru(
                        "r",
                        RefreshToken.emitirNovoLogin(
                                usuario.getId(), "h", OffsetDateTime.now().plusDays(30))));
        when(mapper.toResponse(usuario)).thenReturn(response);

        TokenResponseDto resultado = useCase.executar(new LoginRequestDto("u@sep.test", "senha"));

        assertThat(resultado.mfaRequired()).isFalse();
        assertThat(resultado.accessToken()).isEqualTo("jwt");
    }

    @Test
    void senhaInvalidaLancaBadCredentials() {
        Usuario usuario = Usuario.criar("admin@sep.test", "$2a$hash", Role.ADMIN);
        when(repository.findByUsername("admin@sep.test")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> useCase.executar(new LoginRequestDto("admin@sep.test", "errada")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void usuarioInexistenteLancaBadCredentials() {
        when(repository.findByUsername("nao@existe.test")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(new LoginRequestDto("nao@existe.test", "123456")))
                .isInstanceOf(BadCredentialsException.class);
    }
}
