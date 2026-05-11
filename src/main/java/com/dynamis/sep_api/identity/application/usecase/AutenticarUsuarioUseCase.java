package com.dynamis.sep_api.identity.application.usecase;

import com.dynamis.sep_api.identity.application.service.LockoutService;
import com.dynamis.sep_api.identity.application.service.MfaChallengeService;
import com.dynamis.sep_api.identity.application.service.RefreshTokenService;
import com.dynamis.sep_api.identity.application.service.RefreshTokenService.TokenCru;
import com.dynamis.sep_api.identity.domain.model.LoginAttemptStatus;
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

import java.util.Optional;
import java.util.UUID;

/**
 * Login com senha (Sprint 3) + MFA challenge (Task 5.3) + lockout (Task 5.4).
 *
 * <p>Sequencia:
 *
 * <ol>
 *   <li>{@link LockoutService#verificar} primeiro — conta ja bloqueada nao chega a tentar senha.
 *   <li>Resolve usuario e valida senha; em falha registra tentativa e avalia lockout.
 *   <li>Se MFA ATIVO emite challenge; senao emite access + refresh tokens.
 * </ol>
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
    private final LockoutService lockoutService;
    private final RegistrarTentativaLoginUseCase registrarTentativaLogin;

    public AutenticarUsuarioUseCase(
            UsuarioRepository repository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider tokenProvider,
            JwtProperties jwtProperties,
            UsuarioMapper mapper,
            UsuarioTotpSecretRepository totpRepository,
            MfaChallengeService mfaChallengeService,
            RefreshTokenService refreshTokenService,
            LockoutService lockoutService,
            RegistrarTentativaLoginUseCase registrarTentativaLogin) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.jwtProperties = jwtProperties;
        this.mapper = mapper;
        this.totpRepository = totpRepository;
        this.mfaChallengeService = mfaChallengeService;
        this.refreshTokenService = refreshTokenService;
        this.lockoutService = lockoutService;
        this.registrarTentativaLogin = registrarTentativaLogin;
    }

    @Transactional
    public TokenResponseDto executar(LoginRequestDto dto, String ip, String userAgent) {
        lockoutService.verificar(dto.username());

        Optional<Usuario> usuarioOpt = repository.findByUsername(dto.username());
        if (usuarioOpt.isEmpty()) {
            registrarTentativaLogin.registrar(
                    null, dto.username(), ip, userAgent, LoginAttemptStatus.USUARIO_INEXISTENTE);
            lockoutService.avaliarPosFalha(null, dto.username());
            throw new BadCredentialsException("Credenciais invalidas");
        }
        Usuario usuario = usuarioOpt.get();
        if (!passwordEncoder.matches(dto.password(), usuario.getPassword())) {
            registrarTentativaLogin.registrar(
                    usuario.getId(), dto.username(), ip, userAgent, LoginAttemptStatus.SENHA_INVALIDA);
            lockoutService.avaliarPosFalha(usuario.getId(), dto.username());
            throw new BadCredentialsException("Credenciais invalidas");
        }

        if (mfaAtivoPara(usuario.getId())) {
            registrarTentativaLogin.registrar(
                    usuario.getId(), dto.username(), ip, userAgent, LoginAttemptStatus.TOTP_NECESSARIO);
            UUID challengeId = mfaChallengeService.iniciar(usuario.getId());
            return TokenResponseDto.desafioMfa(challengeId);
        }

        registrarTentativaLogin.registrar(usuario.getId(), dto.username(), ip, userAgent, LoginAttemptStatus.SUCESSO);
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
