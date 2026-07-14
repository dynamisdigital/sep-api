package com.dynamis.sep_api.pix.application.listener;

import com.dynamis.sep_api.pix.domain.event.PixChaveCadastradaEvent;
import com.dynamis.sep_api.pix.domain.event.PixChaveRemovidaEvent;
import com.dynamis.sep_api.pix.domain.vo.TipoChavePix;
import com.dynamis.sep_api.shared.audit.AuditLogSegurancaService;
import com.dynamis.sep_api.shared.audit.TipoEventoSeguranca;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PixChaveAuditListenerTest {

    private final AuditLogSegurancaService auditLogService = mock(AuditLogSegurancaService.class);
    private final PixChaveAuditListener listener = new PixChaveAuditListener(auditLogService, new ObjectMapper());

    private final UUID chaveId = UUID.randomUUID();
    private final UUID contaEscrowId = UUID.randomUUID();
    private final UUID operadorId = UUID.randomUUID();

    @Test
    void aoCadastrar_gravaComOperadorEDetalhesMinimos() {
        listener.aoCadastrar(new PixChaveCadastradaEvent(chaveId, contaEscrowId, TipoChavePix.EMAIL, operadorId));

        ArgumentCaptor<String> detalhes = ArgumentCaptor.forClass(String.class);
        verify(auditLogService)
                .gravar(eq(TipoEventoSeguranca.PIX_CHAVE_CADASTRADA), eq(operadorId), detalhes.capture());
        assertThat(detalhes.getValue())
                .contains(chaveId.toString())
                .contains(contaEscrowId.toString())
                .contains("EMAIL")
                .contains("ATIVA")
                .doesNotContain("valor")
                .doesNotContain("hash")
                .doesNotContain("mascar")
                .doesNotContain("provider")
                .doesNotContain("idempotency");
    }

    @Test
    void aoRemover_gravaComOperadorEDetalhesMinimos() {
        listener.aoRemover(new PixChaveRemovidaEvent(chaveId, contaEscrowId, TipoChavePix.EVP, operadorId));

        ArgumentCaptor<String> detalhes = ArgumentCaptor.forClass(String.class);
        verify(auditLogService).gravar(eq(TipoEventoSeguranca.PIX_CHAVE_REMOVIDA), eq(operadorId), detalhes.capture());
        assertThat(detalhes.getValue())
                .contains(chaveId.toString())
                .contains("EVP")
                .contains("INATIVA")
                .doesNotContain("valor")
                .doesNotContain("hash")
                .doesNotContain("provider")
                .doesNotContain("idempotency");
    }
}
