package com.dynamis.sep_api.identity.application.usecase;

import com.dynamis.sep_api.identity.application.service.MfaChallengeService;
import com.dynamis.sep_api.identity.application.service.RefreshTokenService;
import com.dynamis.sep_api.identity.application.service.RefreshTokenService.TokenCru;
import com.dynamis.sep_api.identity.domain.model.MfaStatus;
import com.dynamis.sep_api.identity.domain.model.UsuarioTotpSecret;
import com.dynamis.sep_api.identity.infrastructure.persistence.UsuarioTotpSecretRepository;
import com.dynamis.sep_api.identity.infrastructure.security.JwtProperties;
import com.dynamis.sep_api.identity.infrastructure.security.JwtTokenProvider;
import com.dynamis.sep_api.identity.web.dto.LoginRequestDto;
import com.dynamis.sep_api.identity.web.dto.TokenResponseDto;
import com.dynamis.sep_api.usuarios.domain.model.Usuario;
import com.dynamis.sep_api.usuarios.infrastructure.persistence.UsuarioRepository;
import com.dynamis.sep_api.usuarios.web.mapper.UsuarioMapper;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Login com senha (Sprint 3) + MFA + refresh token (Sprint 5 Task 5.3).
 *
 * <p>Fluxo:
 *
 * <ul>
 *   <li>Senha valida e MFA <b>nao</b> ativo: emite access + refresh tokens imediatamente.
 *   <li>Senha valida e MFA ATIVO: emite somente {@code mfaChallengeId}; cliente apresenta TOTP em
 *       {@code /auth/totp/verify} para concluir o login.
 * </ul>
 */
@Service
public class AutenticarUsuarioUseCase {

    private final UsuarioRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final JwtProperties jwtProperties;
    private final UsuarioMapper mapper;
    private final UsuarioTotpSecretRepository totpRepository;
    private final MfaChallengeService mfaChallengeService;
    private final RefreshTokenService refreshTokenService;

    public AutenticarUsuarioUseCase(
            UsuarioRepository repository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider tokenProvider,
            JwtProperties jwtProperties,
            UsuarioMapper mapper,
            UsuarioTotpSecretRepository totpRepository,
            MfaChallengeService mfaChallengeService,
            RefreshTokenService refreshTokenService) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.jwtProperties = jwtProperties;
        this.mapper = mapper;
        this.totpRepository = totpRepository;
        this.mfaChallengeService = mfaChallengeService;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public TokenResponseDto executar(LoginRequestDto dto) {
        Usuario usuario = repository
                .findByUsername(dto.username())
                .orElseThrow(() -> new BadCredentialsException("Credenciais invalidas"));
        if (!passwordEncoder.matches(dto.password(), usuario.getPassword())) {
            throw new BadCredentialsException("Credenciais invalidas");
        }

        if (mfaAtivoPara(usuario.getId())) {
            UUID challengeId = mfaChallengeService.iniciar(usuario.getId());
            return TokenResponseDto.desafioMfa(challengeId);
        }

        return emitirSessao(usuario);
    }

    private boolean mfaAtivoPara(UUID usuarioId) {
        return totpRepository
                .findByUsuarioId(usuarioId)
                .map(UsuarioTotpSecret::getStatus)
                .filter(status -> status == MfaStatus.ATIVO)
                .isPresent();
    }

    private TokenResponseDto emitirSessao(Usuario usuario) {
        String accessToken = tokenProvider.gerarToken(usuario);
        TokenCru refresh = refreshTokenService.emitirParaNovoLogin(usuario.getId());
        return TokenResponseDto.comTokens(
                accessToken, jwtProperties.getAccessExpirationSeconds(), refresh.token(), mapper.toResponse(usuario));
    }
}
