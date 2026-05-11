package com.dynamis.sep_api.identity.application.usecase;

import com.dynamis.sep_api.identity.application.exception.MfaChallengeInvalidoException;
import com.dynamis.sep_api.identity.application.exception.MfaNaoHabilitadoException;
import com.dynamis.sep_api.identity.application.exception.TotpInvalidoException;
import com.dynamis.sep_api.identity.application.service.BackupCodeService;
import com.dynamis.sep_api.identity.application.service.MfaChallengeService;
import com.dynamis.sep_api.identity.application.service.RefreshTokenService;
import com.dynamis.sep_api.identity.application.service.RefreshTokenService.TokenCru;
import com.dynamis.sep_api.identity.domain.model.RefreshToken;
import com.dynamis.sep_api.identity.domain.model.UsuarioTotpSecret;
import com.dynamis.sep_api.identity.infrastructure.persistence.UsuarioTotpSecretRepository;
import com.dynamis.sep_api.identity.infrastructure.security.JwtProperties;
import com.dynamis.sep_api.identity.infrastructure.security.JwtTokenProvider;
import com.dynamis.sep_api.identity.infrastructure.totp.GoogleAuthAdapter;
import com.dynamis.sep_api.identity.infrastructure.totp.TotpCryptoService;
import com.dynamis.sep_api.identity.web.dto.TokenResponseDto;
import com.dynamis.sep_api.usuarios.domain.model.Role;
import com.dynamis.sep_api.usuarios.domain.model.Usuario;
import com.dynamis.sep_api.usuarios.infrastructure.persistence.UsuarioRepository;
import com.dynamis.sep_api.usuarios.web.dto.UsuarioResponseDto;
import com.dynamis.sep_api.usuarios.web.mapper.UsuarioMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VerificarTotpUseCaseTest {

    private UsuarioRepository usuarioRepository;
    private UsuarioTotpSecretRepository totpRepository;
    private BackupCodeService backupCodeService;
    private GoogleAuthAdapter googleAuth;
    private TotpCryptoService crypto;
    private MfaChallengeService challengeService;
    private JwtTokenProvider jwtTokenProvider;
    private JwtProperties jwtProperties;
    private RefreshTokenService refreshTokenService;
    private UsuarioMapper usuarioMapper;
    private VerificarTotpUseCase useCase;

    @BeforeEach
    void setup() {
        usuarioRepository = mock(UsuarioRepository.class);
        totpRepository = mock(UsuarioTotpSecretRepository.class);
        backupCodeService = mock(BackupCodeService.class);
        googleAuth = mock(GoogleAuthAdapter.class);
        crypto = mock(TotpCryptoService.class);
        challengeService = mock(MfaChallengeService.class);
        jwtTokenProvider = mock(JwtTokenProvider.class);
        jwtProperties = mock(JwtProperties.class);
        refreshTokenService = mock(RefreshTokenService.class);
        usuarioMapper = mock(UsuarioMapper.class);
        useCase = new VerificarTotpUseCase(
                usuarioRepository,
                totpRepository,
                backupCodeService,
                googleAuth,
                crypto,
                challengeService,
                jwtTokenProvider,
                jwtProperties,
                refreshTokenService,
                usuarioMapper);
    }

    private UsuarioTotpSecret secretAtivo(UUID id) {
        UsuarioTotpSecret s = UsuarioTotpSecret.iniciar(id, "cifrado");
        s.ativar();
        return s;
    }

    private void mockarEmissaoTokens(Usuario usuario, UsuarioResponseDto dto) {
        when(usuarioRepository.findById(usuario.getId())).thenReturn(Optional.of(usuario));
        when(jwtTokenProvider.gerarToken(usuario)).thenReturn("access-novo");
        when(jwtProperties.getAccessExpirationSeconds()).thenReturn(900L);
        RefreshToken persistido = RefreshToken.emitirNovoLogin(
                usuario.getId(), "hash", OffsetDateTime.now().plusDays(30));
        when(refreshTokenService.emitirParaNovoLogin(usuario.getId()))
                .thenReturn(new TokenCru("refresh-novo", persistido));
        when(usuarioMapper.toResponse(usuario)).thenReturn(dto);
    }

    private UsuarioResponseDto dto(UUID id) {
        return new UsuarioResponseDto(
                id, "u@sep.test", Role.CLIENTE, OffsetDateTime.now(), OffsetDateTime.now(), "system", "system");
    }

    @Test
    void codigoTotpValidoEmiteAccessERefresh() {
        UUID challengeId = UUID.randomUUID();
        Usuario usuario = Usuario.criar("u@sep.test", "h", Role.CLIENTE);
        when(challengeService.consumir(challengeId)).thenReturn(usuario.getId());
        when(totpRepository.findByUsuarioId(usuario.getId())).thenReturn(Optional.of(secretAtivo(usuario.getId())));
        when(crypto.decifrar("cifrado")).thenReturn("SECRET");
        when(googleAuth.validarCodigo("SECRET", 123456)).thenReturn(true);
        mockarEmissaoTokens(usuario, dto(usuario.getId()));

        TokenResponseDto resp = useCase.executar(challengeId, "123456");

        assertThat(resp.accessToken()).isEqualTo("access-novo");
        assertThat(resp.refreshToken()).isEqualTo("refresh-novo");
        assertThat(resp.mfaRequired()).isFalse();
    }

    @Test
    void backupCodeValidoEmiteTokens() {
        UUID challengeId = UUID.randomUUID();
        Usuario usuario = Usuario.criar("u@sep.test", "h", Role.CLIENTE);
        when(challengeService.consumir(challengeId)).thenReturn(usuario.getId());
        when(totpRepository.findByUsuarioId(usuario.getId())).thenReturn(Optional.of(secretAtivo(usuario.getId())));
        when(backupCodeService.consumir(usuario.getId(), "AAAA1111")).thenReturn(true);
        mockarEmissaoTokens(usuario, dto(usuario.getId()));

        TokenResponseDto resp = useCase.executar(challengeId, "AAAA1111");

        assertThat(resp.accessToken()).isEqualTo("access-novo");
    }

    @Test
    void codigoErradoDevolveChallengeELanca400() {
        UUID challengeId = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        when(challengeService.consumir(challengeId)).thenReturn(usuarioId);
        when(totpRepository.findByUsuarioId(usuarioId)).thenReturn(Optional.of(secretAtivo(usuarioId)));
        when(crypto.decifrar("cifrado")).thenReturn("SECRET");
        when(googleAuth.validarCodigo("SECRET", 999999)).thenReturn(false);
        when(backupCodeService.consumir(usuarioId, "999999")).thenReturn(false);

        assertThatThrownBy(() -> useCase.executar(challengeId, "999999")).isInstanceOf(TotpInvalidoException.class);
        verify(challengeService).devolver(challengeId, usuarioId);
    }

    @Test
    void challengeInvalidoLanca400() {
        UUID challengeId = UUID.randomUUID();
        when(challengeService.consumir(challengeId)).thenThrow(new MfaChallengeInvalidoException());

        assertThatThrownBy(() -> useCase.executar(challengeId, "123456"))
                .isInstanceOf(MfaChallengeInvalidoException.class);
    }

    @Test
    void mfaNaoAtivoLanca400() {
        UUID challengeId = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        when(challengeService.consumir(challengeId)).thenReturn(usuarioId);
        UsuarioTotpSecret pendente = UsuarioTotpSecret.iniciar(usuarioId, "cifrado");
        when(totpRepository.findByUsuarioId(usuarioId)).thenReturn(Optional.of(pendente));

        assertThatThrownBy(() -> useCase.executar(challengeId, "123456")).isInstanceOf(MfaNaoHabilitadoException.class);
    }

    @Test
    void codigoVazioLanca400SemConsumirChallenge() {
        UUID challengeId = UUID.randomUUID();

        assertThatThrownBy(() -> useCase.executar(challengeId, "")).isInstanceOf(TotpInvalidoException.class);
        assertThatThrownBy(() -> useCase.executar(challengeId, null)).isInstanceOf(TotpInvalidoException.class);
        verify(challengeService, org.mockito.Mockito.never()).consumir(any());
    }
}
