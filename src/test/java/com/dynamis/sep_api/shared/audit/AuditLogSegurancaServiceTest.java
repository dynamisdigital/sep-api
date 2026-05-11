package com.dynamis.sep_api.shared.audit;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AuditLogSegurancaServiceTest {

    @Test
    void gravarSemDetalhesUsaCorpoVazio() {
        AuditLogSegurancaRepository repo = mock(AuditLogSegurancaRepository.class);
        AuditLogSegurancaService svc = new AuditLogSegurancaService(repo);
        UUID id = UUID.randomUUID();

        svc.gravar(TipoEventoSeguranca.MFA_ENABLED, id);

        ArgumentCaptor<AuditLogSeguranca> captor = ArgumentCaptor.forClass(AuditLogSeguranca.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getTipo()).isEqualTo(TipoEventoSeguranca.MFA_ENABLED);
        assertThat(captor.getValue().getUsuarioId()).isEqualTo(id);
        assertThat(captor.getValue().getDetalhes()).isEqualTo("{}");
    }

    @Test
    void gravarComDetalhesPersisteJson() {
        AuditLogSegurancaRepository repo = mock(AuditLogSegurancaRepository.class);
        AuditLogSegurancaService svc = new AuditLogSegurancaService(repo);
        UUID id = UUID.randomUUID();

        svc.gravar(TipoEventoSeguranca.PASSWORD_CHANGED, id, "{\"motivo\":\"reset\"}");

        ArgumentCaptor<AuditLogSeguranca> captor = ArgumentCaptor.forClass(AuditLogSeguranca.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getDetalhes()).contains("motivo");
    }

    @Test
    void gravarComIpUserAgent() {
        AuditLogSegurancaRepository repo = mock(AuditLogSegurancaRepository.class);
        AuditLogSegurancaService svc = new AuditLogSegurancaService(repo);
        UUID id = UUID.randomUUID();

        svc.gravar(TipoEventoSeguranca.STEP_UP_OK, id, "127.0.0.1", "ua", "{}");

        ArgumentCaptor<AuditLogSeguranca> captor = ArgumentCaptor.forClass(AuditLogSeguranca.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getIp()).isEqualTo("127.0.0.1");
        assertThat(captor.getValue().getUserAgent()).isEqualTo("ua");
    }
}
