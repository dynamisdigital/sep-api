package com.dynamis.sep_api.usuarios.application.usecase;

import com.dynamis.sep_api.shared.exception.AcessoNegadoException;
import com.dynamis.sep_api.usuarios.application.exception.UsuarioNaoEncontradoException;
import com.dynamis.sep_api.usuarios.domain.event.RoleAlteradaEvent;
import com.dynamis.sep_api.usuarios.domain.model.Role;
import com.dynamis.sep_api.usuarios.domain.model.Usuario;
import com.dynamis.sep_api.usuarios.infrastructure.persistence.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlterarRoleUsuarioUseCaseTest {

    private UsuarioRepository repository;
    private ApplicationEventPublisher eventPublisher;
    private AlterarRoleUsuarioUseCase useCase;

    @BeforeEach
    void setup() {
        repository = mock(UsuarioRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        useCase = new AlterarRoleUsuarioUseCase(repository, eventPublisher);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void promoveUsuarioParaFinanceiroEPublicaEvento() {
        UUID alvoId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        Usuario alvo = Usuario.criar("user@sep.test", "hash", Role.CLIENTE);
        when(repository.findById(alvoId)).thenReturn(Optional.of(alvo));

        Usuario salvo = useCase.executar(alvoId, Role.FINANCEIRO, adminId);

        assertThat(salvo.getRole()).isEqualTo(Role.FINANCEIRO);
        verify(repository).save(alvo);
        ArgumentCaptor<RoleAlteradaEvent> captor = ArgumentCaptor.forClass(RoleAlteradaEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        RoleAlteradaEvent event = captor.getValue();
        assertThat(event.atorAdminId()).isEqualTo(adminId);
        assertThat(event.usuarioAlvoId()).isEqualTo(alvoId);
        assertThat(event.roleAnterior()).isEqualTo(Role.CLIENTE);
        assertThat(event.roleNova()).isEqualTo(Role.FINANCEIRO);
    }

    @Test
    void rejeitaAlteracaoDaPropriaRole() {
        UUID adminId = UUID.randomUUID();

        assertThatThrownBy(() -> useCase.executar(adminId, Role.FINANCEIRO, adminId))
                .isInstanceOf(AcessoNegadoException.class)
                .hasMessageContaining("propria role");
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void usuarioInexistenteLanca404() {
        UUID alvoId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        when(repository.findById(alvoId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(alvoId, Role.FINANCEIRO, adminId))
                .isInstanceOf(UsuarioNaoEncontradoException.class);
    }

    @Test
    void roleIgualAoAtualEhNoopSemEvento() {
        UUID alvoId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        Usuario alvo = Usuario.criar("user@sep.test", "hash", Role.FINANCEIRO);
        when(repository.findById(alvoId)).thenReturn(Optional.of(alvo));

        useCase.executar(alvoId, Role.FINANCEIRO, adminId);

        verify(repository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }
}
