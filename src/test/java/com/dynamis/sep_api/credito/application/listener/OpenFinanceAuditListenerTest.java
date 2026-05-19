package com.dynamis.sep_api.credito.application.listener;

import com.dynamis.sep_api.credito.domain.event.OpenFinanceAutorizadoEvent;
import com.dynamis.sep_api.credito.domain.event.OpenFinanceConsentimentoIniciadoEvent;
import com.dynamis.sep_api.credito.domain.event.OpenFinanceDadosRecebidosEvent;
import com.dynamis.sep_api.credito.domain.event.OpenFinanceNegadoEvent;
import com.dynamis.sep_api.credito.domain.event.OpenFinanceReavaliacaoEvent;
import com.dynamis.sep_api.credito.domain.vo.StatusProposta;
import com.dynamis.sep_api.shared.audit.AuditLogSegurancaService;
import com.dynamis.sep_api.shared.audit.TipoEventoSeguranca;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OpenFinanceAuditListenerTest {

    private AuditLogSegurancaService auditService;
    private OpenFinanceAuditListener listener;

    @BeforeEach
    void setup() {
        auditService = mock(AuditLogSegurancaService.class);
        listener = new OpenFinanceAuditListener(auditService, new ObjectMapper());
    }

    @Test
    void consentimentoIniciadoGravaAuditComIdsSemPii() {
        UUID consentimentoId = UUID.randomUUID();
        UUID propostaId = UUID.randomUUID();
        UUID tomadorId = UUID.randomUUID();

        listener.aoIniciar(
                new OpenFinanceConsentimentoIniciadoEvent(consentimentoId, propostaId, tomadorId, "ext-celcoin-1"));

        ArgumentCaptor<String> detalhes = ArgumentCaptor.forClass(String.class);
        verify(auditService)
                .gravar(eq(TipoEventoSeguranca.OPEN_FINANCE_CONSENTIMENTO_INICIADO), eq(tomadorId), detalhes.capture());
        assertThat(detalhes.getValue())
                .contains(consentimentoId.toString())
                .contains(propostaId.toString())
                .contains("ext-celcoin-1")
                .doesNotContain("cpf")
                .doesNotContain("CPF");
    }

    @Test
    void autorizadoGravaAudit() {
        UUID consentimentoId = UUID.randomUUID();
        UUID propostaId = UUID.randomUUID();
        UUID tomadorId = UUID.randomUUID();

        listener.aoAutorizar(new OpenFinanceAutorizadoEvent(consentimentoId, propostaId, tomadorId, "ext-1"));

        verify(auditService)
                .gravar(
                        eq(TipoEventoSeguranca.OPEN_FINANCE_AUTORIZADO),
                        eq(tomadorId),
                        org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void negadoGravaAudit() {
        UUID consentimentoId = UUID.randomUUID();
        UUID propostaId = UUID.randomUUID();
        UUID tomadorId = UUID.randomUUID();

        listener.aoNegar(new OpenFinanceNegadoEvent(consentimentoId, propostaId, tomadorId));

        verify(auditService)
                .gravar(
                        eq(TipoEventoSeguranca.OPEN_FINANCE_NEGADO),
                        eq(tomadorId),
                        org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void dadosRecebidosGravaAuditComNumeroMeses() {
        UUID movId = UUID.randomUUID();
        UUID consId = UUID.randomUUID();
        UUID propId = UUID.randomUUID();
        UUID tomId = UUID.randomUUID();

        listener.aoRecebimentoDeDados(new OpenFinanceDadosRecebidosEvent(movId, consId, propId, tomId, 6));

        ArgumentCaptor<String> detalhes = ArgumentCaptor.forClass(String.class);
        verify(auditService)
                .gravar(eq(TipoEventoSeguranca.OPEN_FINANCE_DADOS_RECEBIDOS), eq(tomId), detalhes.capture());
        assertThat(detalhes.getValue())
                .contains("\"numeroMesesAvaliados\":6")
                .doesNotContain("transactions")
                .doesNotContain("account_number");
    }

    @Test
    void reavaliacaoGravaScoreEStatusAntesDepois() {
        UUID propId = UUID.randomUUID();
        UUID tomId = UUID.randomUUID();
        UUID consId = UUID.randomUUID();

        listener.aoReavaliar(new OpenFinanceReavaliacaoEvent(
                propId, tomId, consId, 600, 800, StatusProposta.EM_ANALISE, StatusProposta.PRE_APROVADA));

        ArgumentCaptor<String> detalhes = ArgumentCaptor.forClass(String.class);
        verify(auditService).gravar(eq(TipoEventoSeguranca.OPEN_FINANCE_REAVALIACAO), eq(tomId), detalhes.capture());
        assertThat(detalhes.getValue())
                .contains("\"scoreAnterior\":600")
                .contains("\"scoreNovo\":800")
                .contains("\"statusAnterior\":\"EM_ANALISE\"")
                .contains("\"statusNovo\":\"PRE_APROVADA\"");
    }
}
