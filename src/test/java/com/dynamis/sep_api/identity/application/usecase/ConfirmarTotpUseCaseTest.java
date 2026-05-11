package com.dynamis.sep_api.identity.application.usecase;

import com.dynamis.sep_api.identity.application.exception.MfaJaHabilitadoException;
import com.dynamis.sep_api.identity.application.exception.TotpInvalidoException;
import com.dynamis.sep_api.identity.application.exception.TotpSetupNaoIniciadoException;
import com.dynamis.sep_api.identity.domain.model.MfaStatus;
import com.dynamis.sep_api.identity.domain.model.UsuarioTotpSecret;
import com.dynamis.sep_api.identity.infrastructure.persistence.UsuarioTotpSecretRepository;
import com.dynamis.sep_api.identity.infrastructure.totp.GoogleAuthAdapter;
import com.dynamis.sep_api.identity.infrastructure.totp.TotpCryptoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConfirmarTotpUseCaseTest {

    private UsuarioTotpSecretRepository repository;
    private GoogleAuthAdapter googleAuth;
    private TotpCryptoService crypto;
    private ConfirmarTotpUseCase useCase;

    @BeforeEach
    void setup() {
        repository = mock(UsuarioTotpSecretRepository.class);
        googleAuth = mock(GoogleAuthAdapter.class);
        crypto = mock(TotpCryptoService.class);
        useCase = new ConfirmarTotpUseCase(repository, googleAuth, crypto);
    }

    @Test
    void confirmarComCodigoValidoMudaParaAtivo() {
        UUID id = UUID.randomUUID();
        UsuarioTotpSecret pendente = UsuarioTotpSecret.iniciar(id, "cifrado");
        when(repository.findByUsuarioId(id)).thenReturn(Optional.of(pendente));
        when(crypto.decifrar("cifrado")).thenReturn("SECRET");
        when(googleAuth.validarCodigo("SECRET", 123456)).thenReturn(true);

        useCase.executar(id, "123456");

        assertThat(pendente.getStatus()).isEqualTo(MfaStatus.ATIVO);
        verify(repository).save(pendente);
    }

    @Test
    void codigoInvalidoLanca400() {
        UUID id = UUID.randomUUID();
        UsuarioTotpSecret pendente = UsuarioTotpSecret.iniciar(id, "cifrado");
        when(repository.findByUsuarioId(id)).thenReturn(Optional.of(pendente));
        when(crypto.decifrar("cifrado")).thenReturn("SECRET");
        when(googleAuth.validarCodigo("SECRET", 999999)).thenReturn(false);

        assertThatThrownBy(() -> useCase.executar(id, "999999")).isInstanceOf(TotpInvalidoException.class);
        assertThat(pendente.getStatus()).isEqualTo(MfaStatus.PENDENTE);
    }

    @Test
    void semSetupAnteriorLanca400() {
        UUID id = UUID.randomUUID();
        when(repository.findByUsuarioId(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(id, "123456")).isInstanceOf(TotpSetupNaoIniciadoException.class);
    }

    @Test
    void totpJaAtivoLanca409() {
        UUID id = UUID.randomUUID();
        UsuarioTotpSecret ativo = UsuarioTotpSecret.iniciar(id, "cifrado");
        ativo.ativar();
        when(repository.findByUsuarioId(id)).thenReturn(Optional.of(ativo));

        assertThatThrownBy(() -> useCase.executar(id, "123456")).isInstanceOf(MfaJaHabilitadoException.class);
    }

    @Test
    void codigoNaoNumericoLanca400() {
        UUID id = UUID.randomUUID();
        UsuarioTotpSecret pendente = UsuarioTotpSecret.iniciar(id, "cifrado");
        when(repository.findByUsuarioId(id)).thenReturn(Optional.of(pendente));

        assertThatThrownBy(() -> useCase.executar(id, "ABCDEF")).isInstanceOf(TotpInvalidoException.class);
    }
}
