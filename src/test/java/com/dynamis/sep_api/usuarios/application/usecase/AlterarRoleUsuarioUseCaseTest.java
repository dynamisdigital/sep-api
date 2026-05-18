package com.dynamis.sep_api.usuarios.application.usecase;

import com.dynamis.sep_api.shared.audit.AuditLogSegurancaService;
import com.dynamis.sep_api.shared.audit.TipoEventoSeguranca;
import com.dynamis.sep_api.shared.exception.AcessoNegadoException;
import com.dynamis.sep_api.usuarios.application.exception.UsuarioNaoEncontradoException;
import com.dynamis.sep_api.usuarios.domain.model.Role;
import com.dynamis.sep_api.usuarios.domain.model.Usuario;
import com.dynamis.sep_api.usuarios.infrastructure.persistence.UsuarioRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlterarRoleUsuarioUseCaseTest {

    private UsuarioRepository repository;
    private AuditLogSegurancaService auditService;
    private AlterarRoleUsuarioUseCase useCase;

    @BeforeEach
    void setup() {
        repository = mock(UsuarioRepository.class);
        auditService = mock(AuditLogSegurancaService.class);
        useCase = new AlterarRoleUsuarioUseCase(repository, auditService, new ObjectMapper());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void promoveUsuarioParaFinanceiroEAudita() {
        UUID alvoId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        Usuario alvo = Usuario.criar("user@sep.test", "hash", Role.CLIENTE);
        when(repository.findById(alvoId)).thenReturn(Optional.of(alvo));

        Usuario salvo = useCase.executar(alvoId, Role.FINANCEIRO, adminId);

        assertThat(salvo.getRole()).isEqualTo(Role.FINANCEIRO);
        verify(repository).save(alvo);
        verify(auditService).gravar(eq(TipoEventoSeguranca.ROLE_ALTERADO), eq(adminId), contains("FINANCEIRO"));
    }

    @Test
    void rejeitaAlteracaoDaPropriaRole() {
        UUID adminId = UUID.randomUUID();

        assertThatThrownBy(() -> useCase.executar(adminId, Role.FINANCEIRO, adminId))
                .isInstanceOf(AcessoNegadoException.class)
                .hasMessageContaining("propria role");
        verify(auditService, never()).gravar(any(), any(), any());
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
    void roleIgualAoAtualEhNoopSemAuditoria() {
        UUID alvoId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        Usuario alvo = Usuario.criar("user@sep.test", "hash", Role.FINANCEIRO);
        when(repository.findById(alvoId)).thenReturn(Optional.of(alvo));

        useCase.executar(alvoId, Role.FINANCEIRO, adminId);

        verify(repository, never()).save(any());
        verify(auditService, never()).gravar(any(), any(), any());
    }
}
