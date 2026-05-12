package com.dynamis.sep_api.usuarios.application.usecase;

import com.dynamis.sep_api.identity.domain.vo.PasswordPolicy;
import com.dynamis.sep_api.usuarios.application.exception.UsernameJaExisteException;
import com.dynamis.sep_api.usuarios.domain.model.Role;
import com.dynamis.sep_api.usuarios.domain.model.Usuario;
import com.dynamis.sep_api.usuarios.infrastructure.persistence.UsuarioRepository;
import com.dynamis.sep_api.usuarios.web.dto.UsuarioCreateDto;
import com.dynamis.sep_api.usuarios.web.dto.UsuarioInternoCreateDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CriarUsuarioUseCaseTest {

    @Mock
    private UsuarioRepository repository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private PasswordPolicy passwordPolicy;

    @InjectMocks
    private CriarUsuarioUseCase useCase;

    @Test
    void cadastroPublicoSempreCriaCliente() {
        // 5F-FIX-01: payload publico nao carrega role; resultado sempre CLIENTE.
        UsuarioCreateDto dto = new UsuarioCreateDto("cliente@sep.test", "senha-passphrase-segura");
        when(repository.existsByUsername("cliente@sep.test")).thenReturn(false);
        when(passwordEncoder.encode("senha-passphrase-segura")).thenReturn("$2a$10$hash");
        when(repository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Usuario salvo = useCase.executar(dto);

        assertThat(salvo.getUsername()).isEqualTo("cliente@sep.test");
        assertThat(salvo.getRole()).isEqualTo(Role.CLIENTE);
        assertThat(salvo.getPassword()).isEqualTo("$2a$10$hash");
    }

    @Test
    void cadastroInternoCriaAdminQuandoRoleAdmin() {
        UsuarioInternoCreateDto dto =
                new UsuarioInternoCreateDto("operador@sep.test", "outra-passphrase-segura", Role.ADMIN);
        when(repository.existsByUsername("operador@sep.test")).thenReturn(false);
        when(passwordEncoder.encode("outra-passphrase-segura")).thenReturn("$2a$10$h2");
        when(repository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Usuario salvo = useCase.executarInterno(dto);

        assertThat(salvo.getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void cadastroInternoCriaClienteQuandoRoleCliente() {
        UsuarioInternoCreateDto dto =
                new UsuarioInternoCreateDto("interno@sep.test", "passphrase-interna-segura", Role.CLIENTE);
        when(repository.existsByUsername("interno@sep.test")).thenReturn(false);
        when(passwordEncoder.encode("passphrase-interna-segura")).thenReturn("$2a$10$h3");
        when(repository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Usuario salvo = useCase.executarInterno(dto);

        assertThat(salvo.getRole()).isEqualTo(Role.CLIENTE);
    }

    @Test
    void cadastroPublicoLancaUsernameJaExisteQuandoExistsByUsernameRetornaTrue() {
        UsuarioCreateDto dto = new UsuarioCreateDto("cliente@sep.test", "senha-passphrase-segura");
        when(repository.existsByUsername("cliente@sep.test")).thenReturn(true);

        assertThatThrownBy(() -> useCase.executar(dto))
                .isInstanceOf(UsernameJaExisteException.class)
                .hasMessageContaining("cliente@sep.test");

        verify(repository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void cadastroInternoLancaUsernameJaExisteQuandoExistsByUsernameRetornaTrue() {
        UsuarioInternoCreateDto dto =
                new UsuarioInternoCreateDto("operador@sep.test", "outra-passphrase-segura", Role.ADMIN);
        when(repository.existsByUsername("operador@sep.test")).thenReturn(true);

        assertThatThrownBy(() -> useCase.executarInterno(dto))
                .isInstanceOf(UsernameJaExisteException.class)
                .hasMessageContaining("operador@sep.test");

        verify(repository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void senhaPersistidaEAQueVeioDoPasswordEncoder() {
        UsuarioCreateDto dto = new UsuarioCreateDto("cliente@sep.test", "abcdefghijkl");
        when(repository.existsByUsername("cliente@sep.test")).thenReturn(false);
        when(passwordEncoder.encode("abcdefghijkl")).thenReturn("$2a$10$encodedHash");
        when(repository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        useCase.executar(dto);

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getPassword()).isEqualTo("$2a$10$encodedHash");
        assertThat(captor.getValue().getPassword()).isNotEqualTo("abcdefghijkl");
    }

    @Test
    void chamaRepositorySaveApenasUmaVez() {
        UsuarioCreateDto dto = new UsuarioCreateDto("cliente@sep.test", "senha-passphrase-segura");
        when(repository.existsByUsername("cliente@sep.test")).thenReturn(false);
        when(passwordEncoder.encode("senha-passphrase-segura")).thenReturn("$2a$10$h");
        when(repository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        useCase.executar(dto);

        verify(repository, times(1)).save(any(Usuario.class));
    }
}
