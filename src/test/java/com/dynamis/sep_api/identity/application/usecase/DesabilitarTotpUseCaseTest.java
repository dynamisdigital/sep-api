package com.dynamis.sep_api.identity.application.usecase;

import com.dynamis.sep_api.identity.application.exception.MfaNaoHabilitadoException;
import com.dynamis.sep_api.identity.domain.model.MfaStatus;
import com.dynamis.sep_api.identity.domain.model.UsuarioTotpSecret;
import com.dynamis.sep_api.identity.infrastructure.persistence.UsuarioBackupCodeRepository;
import com.dynamis.sep_api.identity.infrastructure.persistence.UsuarioTotpSecretRepository;
import com.dynamis.sep_api.usuarios.application.exception.SenhaAtualIncorretaException;
import com.dynamis.sep_api.usuarios.application.exception.UsuarioNaoEncontradoException;
import com.dynamis.sep_api.usuarios.domain.model.Role;
import com.dynamis.sep_api.usuarios.domain.model.Usuario;
import com.dynamis.sep_api.usuarios.infrastructure.persistence.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DesabilitarTotpUseCaseTest {

    private UsuarioRepository usuarioRepository;
    private UsuarioTotpSecretRepository totpRepository;
    private UsuarioBackupCodeRepository backupCodeRepository;
    private PasswordEncoder passwordEncoder;
    private com.dynamis.sep_api.shared.audit.AuditLogSegurancaService auditService;
    private DesabilitarTotpUseCase useCase;

    @BeforeEach
    void setup() {
        usuarioRepository = mock(UsuarioRepository.class);
        totpRepository = mock(UsuarioTotpSecretRepository.class);
        backupCodeRepository = mock(UsuarioBackupCodeRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        auditService = mock(com.dynamis.sep_api.shared.audit.AuditLogSegurancaService.class);
        useCase = new DesabilitarTotpUseCase(
                usuarioRepository, totpRepository, backupCodeRepository, passwordEncoder, auditService);
    }

    @Test
    void desabilitarComSenhaCorretaMudaParaDesabilitadoEDescartaBackupCodes() {
        UUID id = UUID.randomUUID();
        Usuario usuario = Usuario.criar("u@sep.test", "hash", Role.CLIENTE);
        when(usuarioRepository.findById(id)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("senha-ok", "hash")).thenReturn(true);
        UsuarioTotpSecret ativo = UsuarioTotpSecret.iniciar(id, "cifrado");
        ativo.ativar();
        when(totpRepository.findByUsuarioId(id)).thenReturn(Optional.of(ativo));

        useCase.executar(id, "senha-ok");

        assertThat(ativo.getStatus()).isEqualTo(MfaStatus.DESABILITADO);
        verify(totpRepository).save(ativo);
        verify(backupCodeRepository).deleteByUsuarioId(id);
    }

    @Test
    void senhaIncorretaLanca400() {
        UUID id = UUID.randomUUID();
        when(usuarioRepository.findById(id)).thenReturn(Optional.of(Usuario.criar("u@sep.test", "hash", Role.CLIENTE)));
        when(passwordEncoder.matches("errada", "hash")).thenReturn(false);

        assertThatThrownBy(() -> useCase.executar(id, "errada")).isInstanceOf(SenhaAtualIncorretaException.class);
    }

    @Test
    void usuarioInexistenteLanca404() {
        UUID id = UUID.randomUUID();
        when(usuarioRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(id, "senha")).isInstanceOf(UsuarioNaoEncontradoException.class);
    }

    @Test
    void mfaNaoHabilitadoLanca400() {
        UUID id = UUID.randomUUID();
        when(usuarioRepository.findById(id)).thenReturn(Optional.of(Usuario.criar("u@sep.test", "hash", Role.CLIENTE)));
        when(passwordEncoder.matches("senha", "hash")).thenReturn(true);
        when(totpRepository.findByUsuarioId(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(id, "senha")).isInstanceOf(MfaNaoHabilitadoException.class);
    }
}
