package com.dynamis.sep_api.pix.application.listener;

import com.dynamis.sep_api.pix.domain.event.PixTransferenciaConcluidaEvent;
import com.dynamis.sep_api.pix.domain.event.PixTransferenciaFalhouEvent;
import com.dynamis.sep_api.pix.domain.event.PixTransferenciaSolicitadaEvent;
import com.dynamis.sep_api.shared.audit.AuditLogSegurancaService;
import com.dynamis.sep_api.shared.audit.TipoEventoSeguranca;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PixDesembolsoAuditListenerTest {

    private final AuditLogSegurancaService auditLogService = mock(AuditLogSegurancaService.class);
    private final PixDesembolsoAuditListener listener =
            new PixDesembolsoAuditListener(auditLogService, new ObjectMapper());

    private final UUID transferenciaId = UUID.randomUUID();
    private final UUID contratoId = UUID.randomUUID();
    private final UUID tomadorId = UUID.randomUUID();

    @Test
    void aoSolicitar_gravaComTomadorEDetalhesSemChave() {
        listener.aoSolicitar(new PixTransferenciaSolicitadaEvent(
                transferenciaId, contratoId, tomadorId, "ext-1", new BigDecimal("10000.00")));

        ArgumentCaptor<String> detalhes = ArgumentCaptor.forClass(String.class);
        verify(auditLogService)
                .gravar(eq(TipoEventoSeguranca.PIX_TRANSFERENCIA_SOLICITADA), eq(tomadorId), detalhes.capture());
        assertThat(detalhes.getValue())
                .contains(transferenciaId.toString())
                .contains(contratoId.toString())
                .contains("10000.00")
                .doesNotContain("chave");
    }

    @Test
    void aoConcluir_gravaConcluida() {
        listener.aoConcluir(new PixTransferenciaConcluidaEvent(transferenciaId, contratoId, tomadorId, "ext-1"));

        verify(auditLogService)
                .gravar(
                        eq(TipoEventoSeguranca.PIX_TRANSFERENCIA_CONCLUIDA),
                        eq(tomadorId),
                        org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void aoFalhar_gravaFalhou() {
        listener.aoFalhar(new PixTransferenciaFalhouEvent(transferenciaId, contratoId, tomadorId, "timeout"));

        ArgumentCaptor<String> detalhes = ArgumentCaptor.forClass(String.class);
        verify(auditLogService)
                .gravar(eq(TipoEventoSeguranca.PIX_TRANSFERENCIA_FALHOU), eq(tomadorId), detalhes.capture());
        assertThat(detalhes.getValue()).contains("timeout");
    }
}
