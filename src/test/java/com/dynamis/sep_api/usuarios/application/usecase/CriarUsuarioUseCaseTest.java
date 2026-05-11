package com.dynamis.sep_api.usuarios.application.usecase;

import com.dynamis.sep_api.identity.domain.vo.PasswordPolicy;
import com.dynamis.sep_api.usuarios.application.exception.UsernameJaExisteException;
import com.dynamis.sep_api.usuarios.domain.model.Role;
import com.dynamis.sep_api.usuarios.domain.model.Usuario;
import com.dynamis.sep_api.usuarios.infrastructure.persistence.UsuarioRepository;
import com.dynamis.sep_api.usuarios.web.dto.UsuarioCreateDto;
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
    void criaAdminValido() {
        UsuarioCreateDto dto = new UsuarioCreateDto("admin@sep.test", "senha-passphrase-segura", Role.ADMIN);
        when(repository.existsByUsername("admin@sep.test")).thenReturn(false);
        when(passwordEncoder.encode("senha-passphrase-segura")).thenReturn("$2a$10$hash");
        when(repository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Usuario salvo = useCase.executar(dto);

        assertThat(salvo.getUsername()).isEqualTo("admin@sep.test");
        assertThat(salvo.getRole()).isEqualTo(Role.ADMIN);
        assertThat(salvo.getPassword()).isEqualTo("$2a$10$hash");
    }

    @Test
    void criaClienteValido() {
        UsuarioCreateDto dto = new UsuarioCreateDto("cliente@sep.test", "outra-passphrase-segura", Role.CLIENTE);
        when(repository.existsByUsername("cliente@sep.test")).thenReturn(false);
        when(passwordEncoder.encode("outra-passphrase-segura")).thenReturn("$2a$10$hash2");
        when(repository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Usuario salvo = useCase.executar(dto);

        assertThat(salvo.getRole()).isEqualTo(Role.CLIENTE);
    }

    @Test
    void lancaUsernameJaExisteQuandoExistsByUsernameRetornaTrue() {
        UsuarioCreateDto dto = new UsuarioCreateDto("admin@sep.test", "senha-passphrase-segura", Role.ADMIN);
        when(repository.existsByUsername("admin@sep.test")).thenReturn(true);

        assertThatThrownBy(() -> useCase.executar(dto))
                .isInstanceOf(UsernameJaExisteException.class)
                .hasMessageContaining("admin@sep.test");

        verify(repository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void senhaPersistidaEAQueVeioDoPasswordEncoder() {
        UsuarioCreateDto dto = new UsuarioCreateDto("admin@sep.test", "abcdefghijkl", Role.ADMIN);
        when(repository.existsByUsername("admin@sep.test")).thenReturn(false);
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
        UsuarioCreateDto dto = new UsuarioCreateDto("admin@sep.test", "senha-passphrase-segura", Role.ADMIN);
        when(repository.existsByUsername("admin@sep.test")).thenReturn(false);
        when(passwordEncoder.encode("senha-passphrase-segura")).thenReturn("$2a$10$h");
        when(repository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        useCase.executar(dto);

        verify(repository, times(1)).save(any(Usuario.class));
    }
}
