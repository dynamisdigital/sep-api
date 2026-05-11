package com.dynamis.sep_api.identity.application.usecase;

import com.dynamis.sep_api.identity.application.exception.MfaJaHabilitadoException;
import com.dynamis.sep_api.identity.application.service.BackupCodeService;
import com.dynamis.sep_api.identity.domain.model.MfaStatus;
import com.dynamis.sep_api.identity.domain.model.UsuarioTotpSecret;
import com.dynamis.sep_api.identity.infrastructure.persistence.UsuarioTotpSecretRepository;
import com.dynamis.sep_api.identity.infrastructure.totp.GoogleAuthAdapter;
import com.dynamis.sep_api.identity.infrastructure.totp.TotpCryptoService;
import com.dynamis.sep_api.identity.web.dto.TotpSetupResponseDto;
import com.dynamis.sep_api.usuarios.application.exception.UsuarioNaoEncontradoException;
import com.dynamis.sep_api.usuarios.domain.model.Role;
import com.dynamis.sep_api.usuarios.domain.model.Usuario;
import com.dynamis.sep_api.usuarios.infrastructure.persistence.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HabilitarTotpUseCaseTest {

    private UsuarioRepository usuarioRepository;
    private UsuarioTotpSecretRepository totpRepository;
    private GoogleAuthAdapter googleAuth;
    private TotpCryptoService crypto;
    private BackupCodeService backupCodeService;
    private HabilitarTotpUseCase useCase;

    @BeforeEach
    void setup() {
        usuarioRepository = mock(UsuarioRepository.class);
        totpRepository = mock(UsuarioTotpSecretRepository.class);
        googleAuth = mock(GoogleAuthAdapter.class);
        crypto = mock(TotpCryptoService.class);
        backupCodeService = mock(BackupCodeService.class);
        useCase = new HabilitarTotpUseCase(usuarioRepository, totpRepository, googleAuth, crypto, backupCodeService);
    }

    @Test
    void executarRetornaSetupCompleto() {
        UUID id = UUID.randomUUID();
        Usuario usuario = Usuario.criar("ok@sep.test", "hash", Role.CLIENTE);
        when(usuarioRepository.findById(id)).thenReturn(Optional.of(usuario));
        when(totpRepository.findByUsuarioId(id)).thenReturn(Optional.empty());
        when(googleAuth.gerarSecretBase32()).thenReturn("SECRETBASE32");
        when(crypto.cifrar("SECRETBASE32")).thenReturn("cifrado");
        when(googleAuth.gerarOtpAuthUri(any(), any())).thenReturn("otpauth://uri");
        when(googleAuth.gerarQrCodeDataUrl(any())).thenReturn("data:image/png;base64,X");
        when(backupCodeService.gerarParaUsuario(id)).thenReturn(List.of("AAAA1111", "BBBB2222"));

        TotpSetupResponseDto resp = useCase.executar(id);

        assertThat(resp.secretBase32()).isEqualTo("SECRETBASE32");
        assertThat(resp.otpAuthUri()).isEqualTo("otpauth://uri");
        assertThat(resp.qrCodeDataUrl()).startsWith("data:image/png;base64,");
        assertThat(resp.backupCodes()).hasSize(2);

        ArgumentCaptor<UsuarioTotpSecret> captor = ArgumentCaptor.forClass(UsuarioTotpSecret.class);
        verify(totpRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(MfaStatus.PENDENTE);
        assertThat(captor.getValue().getSecretCifrado()).isEqualTo("cifrado");
    }

    @Test
    void usuarioInexistenteLanca404() {
        UUID id = UUID.randomUUID();
        when(usuarioRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(id)).isInstanceOf(UsuarioNaoEncontradoException.class);
    }

    @Test
    void totpJaAtivoLanca409() {
        UUID id = UUID.randomUUID();
        when(usuarioRepository.findById(id)).thenReturn(Optional.of(Usuario.criar("u@sep.test", "h", Role.CLIENTE)));
        UsuarioTotpSecret existente = UsuarioTotpSecret.iniciar(id, "cifrado-antigo");
        existente.ativar();
        when(totpRepository.findByUsuarioId(id)).thenReturn(Optional.of(existente));

        assertThatThrownBy(() -> useCase.executar(id)).isInstanceOf(MfaJaHabilitadoException.class);
        verify(totpRepository, never()).save(any());
    }

    @Test
    void totpPendenteAnteriorEhSobrescrito() {
        UUID id = UUID.randomUUID();
        when(usuarioRepository.findById(id)).thenReturn(Optional.of(Usuario.criar("u@sep.test", "h", Role.CLIENTE)));
        UsuarioTotpSecret pendente = UsuarioTotpSecret.iniciar(id, "antigo-cifrado");
        when(totpRepository.findByUsuarioId(id)).thenReturn(Optional.of(pendente));
        when(googleAuth.gerarSecretBase32()).thenReturn("NOVO");
        when(crypto.cifrar("NOVO")).thenReturn("cifrado-novo");
        when(googleAuth.gerarOtpAuthUri(any(), any())).thenReturn("otpauth://uri");
        when(googleAuth.gerarQrCodeDataUrl(any())).thenReturn("data:png");
        when(backupCodeService.gerarParaUsuario(id)).thenReturn(List.of("CODE0001"));

        TotpSetupResponseDto resp = useCase.executar(id);

        verify(totpRepository).delete(pendente);
        assertThat(resp.secretBase32()).isEqualTo("NOVO");
    }
}
