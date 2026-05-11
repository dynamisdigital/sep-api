package com.dynamis.sep_api.identity.application.usecase;

import com.dynamis.sep_api.identity.application.exception.MfaChallengeInvalidoException;
import com.dynamis.sep_api.identity.application.exception.MfaNaoHabilitadoException;
import com.dynamis.sep_api.identity.application.exception.TotpInvalidoException;
import com.dynamis.sep_api.identity.application.service.BackupCodeService;
import com.dynamis.sep_api.identity.application.service.StepUpChallengeService;
import com.dynamis.sep_api.identity.application.service.StepUpTokenService;
import com.dynamis.sep_api.identity.application.service.StepUpTokenService.TokenCru;
import com.dynamis.sep_api.identity.domain.model.StepUpToken;
import com.dynamis.sep_api.identity.domain.model.UsuarioTotpSecret;
import com.dynamis.sep_api.identity.infrastructure.persistence.UsuarioTotpSecretRepository;
import com.dynamis.sep_api.identity.infrastructure.totp.GoogleAuthAdapter;
import com.dynamis.sep_api.identity.infrastructure.totp.TotpCryptoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CompletarStepUpUseCaseTest {

    private UsuarioTotpSecretRepository totpRepository;
    private BackupCodeService backupCodeService;
    private GoogleAuthAdapter googleAuth;
    private TotpCryptoService crypto;
    private StepUpChallengeService challengeService;
    private StepUpTokenService tokenService;
    private com.dynamis.sep_api.shared.audit.AuditLogSegurancaService auditService;
    private CompletarStepUpUseCase useCase;

    @BeforeEach
    void setup() {
        totpRepository = mock(UsuarioTotpSecretRepository.class);
        backupCodeService = mock(BackupCodeService.class);
        googleAuth = mock(GoogleAuthAdapter.class);
        crypto = mock(TotpCryptoService.class);
        challengeService = mock(StepUpChallengeService.class);
        tokenService = mock(StepUpTokenService.class);
        auditService = mock(com.dynamis.sep_api.shared.audit.AuditLogSegurancaService.class);
        useCase = new CompletarStepUpUseCase(
                totpRepository, backupCodeService, googleAuth, crypto, challengeService, tokenService, auditService);
    }

    private UsuarioTotpSecret secretAtivo(UUID id) {
        UsuarioTotpSecret s = UsuarioTotpSecret.iniciar(id, "cifrado");
        s.ativar();
        return s;
    }

    @Test
    void codigoValidoEmiteStepUpToken() {
        UUID challengeId = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        when(challengeService.consumir(challengeId)).thenReturn(usuarioId);
        when(totpRepository.findByUsuarioId(usuarioId)).thenReturn(Optional.of(secretAtivo(usuarioId)));
        when(crypto.decifrar("cifrado")).thenReturn("SECRET");
        when(googleAuth.validarCodigo("SECRET", 123456)).thenReturn(true);
        StepUpToken persistido =
                StepUpToken.emitir(usuarioId, "hash", OffsetDateTime.now().plusMinutes(5));
        when(tokenService.emitir(usuarioId)).thenReturn(new TokenCru("step-up-cru", persistido));

        String token = useCase.executar(challengeId, "123456", usuarioId);

        assertThat(token).isEqualTo("step-up-cru");
    }

    @Test
    void challengeDeOutroUsuarioLanca403() {
        UUID challengeId = UUID.randomUUID();
        UUID dono = UUID.randomUUID();
        UUID outro = UUID.randomUUID();
        when(challengeService.consumir(challengeId)).thenReturn(dono);

        assertThatThrownBy(() -> useCase.executar(challengeId, "123456", outro))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void codigoInvalidoLanca400() {
        UUID challengeId = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        when(challengeService.consumir(challengeId)).thenReturn(usuarioId);
        when(totpRepository.findByUsuarioId(usuarioId)).thenReturn(Optional.of(secretAtivo(usuarioId)));
        when(crypto.decifrar("cifrado")).thenReturn("SECRET");
        when(googleAuth.validarCodigo("SECRET", 999999)).thenReturn(false);
        when(backupCodeService.consumir(usuarioId, "999999")).thenReturn(false);

        assertThatThrownBy(() -> useCase.executar(challengeId, "999999", usuarioId))
                .isInstanceOf(TotpInvalidoException.class);
    }

    @Test
    void challengeInvalidoLanca() {
        UUID challengeId = UUID.randomUUID();
        when(challengeService.consumir(challengeId)).thenThrow(new MfaChallengeInvalidoException());

        assertThatThrownBy(() -> useCase.executar(challengeId, "123456", UUID.randomUUID()))
                .isInstanceOf(MfaChallengeInvalidoException.class);
    }

    @Test
    void mfaNaoAtivoLanca() {
        UUID challengeId = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        when(challengeService.consumir(challengeId)).thenReturn(usuarioId);
        when(totpRepository.findByUsuarioId(usuarioId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(challengeId, "123456", usuarioId))
                .isInstanceOf(MfaNaoHabilitadoException.class);
    }
}
