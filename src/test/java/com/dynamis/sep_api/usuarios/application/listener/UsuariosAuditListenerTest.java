package com.dynamis.sep_api.usuarios.application.listener;

import com.dynamis.sep_api.shared.audit.AuditLogSegurancaService;
import com.dynamis.sep_api.shared.audit.TipoEventoSeguranca;
import com.dynamis.sep_api.usuarios.domain.event.RoleAlteradaEvent;
import com.dynamis.sep_api.usuarios.domain.model.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class UsuariosAuditListenerTest {

    private AuditLogSegurancaService auditService;
    private UsuariosAuditListener listener;

    @BeforeEach
    void setup() {
        auditService = mock(AuditLogSegurancaService.class);
        listener = new UsuariosAuditListener(auditService, new ObjectMapper());
    }

    @Test
    void roleAlteradaGravaAuditComDetalhes() {
        UUID alvoId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        listener.aoAlterarRole(new RoleAlteradaEvent(adminId, alvoId, Role.CLIENTE, Role.FINANCEIRO));

        ArgumentCaptor<String> detalhes = ArgumentCaptor.forClass(String.class);
        verify(auditService).gravar(eq(TipoEventoSeguranca.ROLE_ALTERADO), eq(adminId), detalhes.capture());
        assertThat(detalhes.getValue())
                .contains(alvoId.toString())
                .contains("\"roleAnterior\":\"CLIENTE\"")
                .contains("\"roleNova\":\"FINANCEIRO\"");
    }
}
