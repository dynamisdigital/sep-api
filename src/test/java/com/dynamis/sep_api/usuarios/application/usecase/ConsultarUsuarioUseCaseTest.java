package com.dynamis.sep_api.usuarios.application.usecase;

import com.dynamis.sep_api.identity.infrastructure.security.UsuarioAutenticado;
import com.dynamis.sep_api.usuarios.application.exception.UsuarioNaoEncontradoException;
import com.dynamis.sep_api.usuarios.domain.model.Role;
import com.dynamis.sep_api.usuarios.domain.model.Usuario;
import com.dynamis.sep_api.usuarios.infrastructure.persistence.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsultarUsuarioUseCaseTest {

    @Mock
    private UsuarioRepository repository;

    @InjectMocks
    private ConsultarUsuarioUseCase useCase;

    @Test
    void adminConsultaUsuarioAlheio() {
        UUID alvoId = UUID.randomUUID();
        UsuarioAutenticado admin = new UsuarioAutenticado(UUID.randomUUID(), "admin@sep.test", Role.ADMIN);
        Usuario alvo = Usuario.criar("alvo@sep.test", "$2a$h", Role.CLIENTE);
        when(repository.findById(alvoId)).thenReturn(Optional.of(alvo));

        Usuario resultado = useCase.executar(alvoId, admin);

        assertThat(resultado).isSameAs(alvo);
    }

    @Test
    void clienteConsultaProprioUsuario() {
        UUID id = UUID.randomUUID();
        UsuarioAutenticado cliente = new UsuarioAutenticado(id, "cliente@sep.test", Role.CLIENTE);
        Usuario proprio = Usuario.criar("cliente@sep.test", "$2a$h", Role.CLIENTE);
        when(repository.findById(id)).thenReturn(Optional.of(proprio));

        Usuario resultado = useCase.executar(id, cliente);

        assertThat(resultado).isSameAs(proprio);
    }

    @Test
    void clienteConsultandoIdAlheioRecebeAccessDenied() {
        UsuarioAutenticado cliente = new UsuarioAutenticado(UUID.randomUUID(), "cliente@sep.test", Role.CLIENTE);
        UUID alheio = UUID.randomUUID();

        assertThatThrownBy(() -> useCase.executar(alheio, cliente)).isInstanceOf(AccessDeniedException.class);

        verify(repository, never()).findById(alheio);
    }

    @Test
    void usuarioInexistenteLancaUsuarioNaoEncontrado() {
        UsuarioAutenticado admin = new UsuarioAutenticado(UUID.randomUUID(), "admin@sep.test", Role.ADMIN);
        UUID alvo = UUID.randomUUID();
        when(repository.findById(alvo)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(alvo, admin)).isInstanceOf(UsuarioNaoEncontradoException.class);
    }
}
