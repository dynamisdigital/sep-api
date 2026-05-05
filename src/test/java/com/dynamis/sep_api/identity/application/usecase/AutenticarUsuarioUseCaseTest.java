package com.dynamis.sep_api.identity.application.usecase;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
    private UsuarioMapper mapper;

    @InjectMocks
    private AutenticarUsuarioUseCase useCase;

    @Test
    void loginValidoRetornaTokenBearerEUsuarioSemPassword() {
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
        when(tokenProvider.gerarToken(usuario)).thenReturn("token-jwt-fake");
        when(tokenProvider.getExpirationSeconds()).thenReturn(3600L);
        when(mapper.toResponse(usuario)).thenReturn(response);

        TokenResponseDto resultado = useCase.executar(new LoginRequestDto("admin@sep.test", "123456"));

        assertThat(resultado.accessToken()).isEqualTo("token-jwt-fake");
        assertThat(resultado.tokenType()).isEqualTo("Bearer");
        assertThat(resultado.expiresIn()).isEqualTo(3600L);
        assertThat(resultado.usuario()).isEqualTo(response);
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
