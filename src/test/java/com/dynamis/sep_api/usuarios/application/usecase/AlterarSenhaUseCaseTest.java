package com.dynamis.sep_api.usuarios.application.usecase;

import com.dynamis.sep_api.identity.infrastructure.security.UsuarioAutenticado;
import com.dynamis.sep_api.usuarios.application.exception.SenhaAtualIncorretaException;
import com.dynamis.sep_api.usuarios.application.exception.UsuarioNaoEncontradoException;
import com.dynamis.sep_api.usuarios.domain.model.Role;
import com.dynamis.sep_api.usuarios.domain.model.Usuario;
import com.dynamis.sep_api.usuarios.infrastructure.persistence.UsuarioRepository;
import com.dynamis.sep_api.usuarios.web.dto.UsuarioSenhaUpdateDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlterarSenhaUseCaseTest {

    @Mock
    private UsuarioRepository repository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AlterarSenhaUseCase useCase;

    @Test
    void usuarioAlteraProprioPasswordComSucesso() {
        Usuario usuario = Usuario.criar("a@sep.test", "$2a$hashAntigo", Role.CLIENTE);
        UUID id = usuario.getId();
        UsuarioAutenticado principal = new UsuarioAutenticado(id, "a@sep.test", Role.CLIENTE);
        when(repository.findById(id)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("123456", "$2a$hashAntigo")).thenReturn(true);
        when(passwordEncoder.encode("abcdef")).thenReturn("$2a$hashNovo");

        useCase.executar(id, new UsuarioSenhaUpdateDto("123456", "abcdef"), principal);

        assertThat(usuario.getPassword()).isEqualTo("$2a$hashNovo");
    }

    @Test
    void usuarioAlterandoSenhaDeTerceiroLancaAccessDenied() {
        UUID alheio = UUID.randomUUID();
        UsuarioAutenticado principal = new UsuarioAutenticado(UUID.randomUUID(), "a@sep.test", Role.CLIENTE);

        assertThatThrownBy(() -> useCase.executar(alheio, new UsuarioSenhaUpdateDto("123456", "abcdef"), principal))
                .isInstanceOf(AccessDeniedException.class);

        verify(repository, never()).findById(alheio);
    }

    @Test
    void senhaAtualIncorretaLancaExcecao() {
        Usuario usuario = Usuario.criar("a@sep.test", "$2a$hashAntigo", Role.CLIENTE);
        UUID id = usuario.getId();
        UsuarioAutenticado principal = new UsuarioAutenticado(id, "a@sep.test", Role.CLIENTE);
        when(repository.findById(id)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("errada", "$2a$hashAntigo")).thenReturn(false);

        assertThatThrownBy(() -> useCase.executar(id, new UsuarioSenhaUpdateDto("errada", "abcdef"), principal))
                .isInstanceOf(SenhaAtualIncorretaException.class);
    }

    @Test
    void novaSenhaPersistidaEHashBCryptNaoTextoClaro() {
        Usuario usuario = Usuario.criar("a@sep.test", "$2a$hashAntigo", Role.CLIENTE);
        UUID id = usuario.getId();
        UsuarioAutenticado principal = new UsuarioAutenticado(id, "a@sep.test", Role.CLIENTE);
        when(repository.findById(id)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("123456", "$2a$hashAntigo")).thenReturn(true);
        when(passwordEncoder.encode("abcdef")).thenReturn("$2a$hashNovo");

        useCase.executar(id, new UsuarioSenhaUpdateDto("123456", "abcdef"), principal);

        assertThat(usuario.getPassword()).startsWith("$2a$").isNotEqualTo("abcdef");
    }

    @Test
    void usuarioInexistenteLancaUsuarioNaoEncontrado() {
        UUID id = UUID.randomUUID();
        UsuarioAutenticado principal = new UsuarioAutenticado(id, "a@sep.test", Role.CLIENTE);
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(id, new UsuarioSenhaUpdateDto("123456", "abcdef"), principal))
                .isInstanceOf(UsuarioNaoEncontradoException.class);
    }
}
