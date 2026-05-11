package com.dynamis.sep_api.identity.application.usecase;

import com.dynamis.sep_api.identity.application.exception.MfaNaoHabilitadoException;
import com.dynamis.sep_api.identity.application.exception.TotpInvalidoException;
import com.dynamis.sep_api.identity.application.service.BackupCodeService;
import com.dynamis.sep_api.identity.domain.model.UsuarioTotpSecret;
import com.dynamis.sep_api.identity.infrastructure.persistence.UsuarioTotpSecretRepository;
import com.dynamis.sep_api.identity.infrastructure.totp.GoogleAuthAdapter;
import com.dynamis.sep_api.identity.infrastructure.totp.TotpCryptoService;
import com.dynamis.sep_api.identity.web.dto.TotpVerifyResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VerificarTotpUseCaseTest {

    private UsuarioTotpSecretRepository totpRepository;
    private BackupCodeService backupCodeService;
    private GoogleAuthAdapter googleAuth;
    private TotpCryptoService crypto;
    private VerificarTotpUseCase useCase;

    @BeforeEach
    void setup() {
        totpRepository = mock(UsuarioTotpSecretRepository.class);
        backupCodeService = mock(BackupCodeService.class);
        googleAuth = mock(GoogleAuthAdapter.class);
        crypto = mock(TotpCryptoService.class);
        useCase = new VerificarTotpUseCase(totpRepository, backupCodeService, googleAuth, crypto);
    }

    private UsuarioTotpSecret secretAtivo(UUID id) {
        UsuarioTotpSecret s = UsuarioTotpSecret.iniciar(id, "cifrado");
        s.ativar();
        return s;
    }

    @Test
    void codigoTotpValidoRetornaVerificado() {
        UUID id = UUID.randomUUID();
        when(totpRepository.findByUsuarioId(id)).thenReturn(Optional.of(secretAtivo(id)));
        when(crypto.decifrar("cifrado")).thenReturn("SECRET");
        when(googleAuth.validarCodigo("SECRET", 123456)).thenReturn(true);

        TotpVerifyResponseDto resp = useCase.executar(id, "123456");

        assertThat(resp.verificado()).isTrue();
        assertThat(resp.usouBackupCode()).isFalse();
    }

    @Test
    void backupCodeValidoRetornaVerificadoMarcandoFlag() {
        UUID id = UUID.randomUUID();
        when(totpRepository.findByUsuarioId(id)).thenReturn(Optional.of(secretAtivo(id)));
        when(backupCodeService.consumir(id, "AAAA1111")).thenReturn(true);

        TotpVerifyResponseDto resp = useCase.executar(id, "AAAA1111");

        assertThat(resp.verificado()).isTrue();
        assertThat(resp.usouBackupCode()).isTrue();
    }

    @Test
    void codigoErradoLanca400() {
        UUID id = UUID.randomUUID();
        when(totpRepository.findByUsuarioId(id)).thenReturn(Optional.of(secretAtivo(id)));
        when(crypto.decifrar("cifrado")).thenReturn("SECRET");
        when(googleAuth.validarCodigo("SECRET", 999999)).thenReturn(false);
        when(backupCodeService.consumir(id, "999999")).thenReturn(false);

        assertThatThrownBy(() -> useCase.executar(id, "999999")).isInstanceOf(TotpInvalidoException.class);
    }

    @Test
    void mfaNaoHabilitadoLanca400() {
        UUID id = UUID.randomUUID();
        when(totpRepository.findByUsuarioId(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(id, "123456")).isInstanceOf(MfaNaoHabilitadoException.class);
    }

    @Test
    void mfaPendenteLanca400() {
        UUID id = UUID.randomUUID();
        UsuarioTotpSecret pendente = UsuarioTotpSecret.iniciar(id, "cifrado");
        when(totpRepository.findByUsuarioId(id)).thenReturn(Optional.of(pendente));

        assertThatThrownBy(() -> useCase.executar(id, "123456")).isInstanceOf(MfaNaoHabilitadoException.class);
    }

    @Test
    void codigoVazioLanca400() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> useCase.executar(id, "")).isInstanceOf(TotpInvalidoException.class);
        assertThatThrownBy(() -> useCase.executar(id, null)).isInstanceOf(TotpInvalidoException.class);
    }
}
