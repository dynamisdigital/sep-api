package com.dynamis.sep_api.credito.application.listener;

import com.dynamis.sep_api.credito.domain.event.ParecerRegistradoEvent;
import com.dynamis.sep_api.credito.domain.event.PropostaAprovadaEvent;
import com.dynamis.sep_api.credito.domain.event.PropostaCriadaEvent;
import com.dynamis.sep_api.credito.domain.event.PropostaRejeitadaEvent;
import com.dynamis.sep_api.credito.domain.vo.DecisaoParecer;
import com.dynamis.sep_api.credito.domain.vo.OrigemDecisao;
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

class CreditoAuditListenerTest {

    private AuditLogSegurancaService auditService;
    private CreditoAuditListener listener;

    @BeforeEach
    void setup() {
        auditService = mock(AuditLogSegurancaService.class);
        listener = new CreditoAuditListener(auditService, new ObjectMapper());
    }

    @Test
    void propostaCriadaGravaAuditComIdsSemDadosSensiveis() {
        UUID propostaId = UUID.randomUUID();
        UUID tomadorId = UUID.randomUUID();
        UUID onbId = UUID.randomUUID();

        listener.aoCriar(new PropostaCriadaEvent(propostaId, tomadorId, onbId));

        ArgumentCaptor<String> detalhes = ArgumentCaptor.forClass(String.class);
        verify(auditService).gravar(eq(TipoEventoSeguranca.PROPOSTA_CRIADA), eq(tomadorId), detalhes.capture());
        assertThat(detalhes.getValue())
                .contains(propostaId.toString())
                .contains(onbId.toString())
                .doesNotContain("cpf")
                .doesNotContain("CPF");
    }

    // PROPOSTA_AVALIADA_MOTOR e gravada SINCRONA pelo PropostaAvaliacaoTransacional —
    // teste especifico vive em PropostaAvaliacaoTransacionalTest (fix code review Task 8.6).

    @Test
    void parecerRegistradoGravaScoreDoMotorEDecisaoManual() {
        UUID propostaId = UUID.randomUUID();
        UUID parecerId = UUID.randomUUID();
        UUID pareceristaId = UUID.randomUUID();

        listener.aoRegistrarParecer(new ParecerRegistradoEvent(
                propostaId,
                parecerId,
                pareceristaId,
                DecisaoParecer.APROVAR,
                "Cliente com bom historico interno",
                850));

        ArgumentCaptor<String> detalhes = ArgumentCaptor.forClass(String.class);
        verify(auditService).gravar(eq(TipoEventoSeguranca.PARECER_REGISTRADO), eq(pareceristaId), detalhes.capture());
        assertThat(detalhes.getValue())
                .contains("\"decisao\":\"APROVAR\"")
                .contains("\"scoreMotorSnapshot\":850")
                .contains(parecerId.toString())
                .contains(propostaId.toString())
                .contains("Cliente com bom historico interno");
    }

    @Test
    void justificativaLongaEhTruncadaNoAudit() {
        UUID propostaId = UUID.randomUUID();
        String longa = "x".repeat(500);

        listener.aoRegistrarParecer(new ParecerRegistradoEvent(
                propostaId, UUID.randomUUID(), UUID.randomUUID(), DecisaoParecer.PENDENCIA, longa, null));

        ArgumentCaptor<String> detalhes = ArgumentCaptor.forClass(String.class);
        verify(auditService).gravar(eq(TipoEventoSeguranca.PARECER_REGISTRADO), any(UUID.class), detalhes.capture());
        // Truncado: 200 chars + "..."
        assertThat(detalhes.getValue()).contains("xxx...");
        assertThat(detalhes.getValue()).doesNotContain("x".repeat(300));
    }

    @Test
    void justificativaComCaracteresEspeciaisSerializadaSemQuebrarJson() {
        UUID propostaId = UUID.randomUUID();
        String maliciosa = "aspa\"quebra\\backslash\nnova-linha";

        listener.aoRegistrarParecer(new ParecerRegistradoEvent(
                propostaId, UUID.randomUUID(), UUID.randomUUID(), DecisaoParecer.REJEITAR, maliciosa, 200));

        ArgumentCaptor<String> detalhes = ArgumentCaptor.forClass(String.class);
        verify(auditService).gravar(eq(TipoEventoSeguranca.PARECER_REGISTRADO), any(UUID.class), detalhes.capture());
        // Caracteres escapados pelo Jackson
        assertThat(detalhes.getValue()).contains("\\\"").contains("\\\\").contains("\\n");
    }

    @Test
    void propostaAprovadaGravaParecerIdETomador() {
        UUID propostaId = UUID.randomUUID();
        UUID tomadorId = UUID.randomUUID();
        UUID parecerId = UUID.randomUUID();

        listener.aoAprovar(new PropostaAprovadaEvent(propostaId, tomadorId, parecerId));

        ArgumentCaptor<String> detalhes = ArgumentCaptor.forClass(String.class);
        verify(auditService).gravar(eq(TipoEventoSeguranca.PROPOSTA_APROVADA), eq(tomadorId), detalhes.capture());
        assertThat(detalhes.getValue()).contains(propostaId.toString()).contains(parecerId.toString());
    }

    @Test
    void propostaRejeitadaPelaMotorOmiteParecerId() {
        UUID propostaId = UUID.randomUUID();
        UUID tomadorId = UUID.randomUUID();

        listener.aoRejeitar(new PropostaRejeitadaEvent(propostaId, tomadorId, OrigemDecisao.MOTOR, null));

        ArgumentCaptor<String> detalhes = ArgumentCaptor.forClass(String.class);
        verify(auditService).gravar(eq(TipoEventoSeguranca.PROPOSTA_REJEITADA), eq(tomadorId), detalhes.capture());
        assertThat(detalhes.getValue()).contains("\"origem\":\"MOTOR\"").doesNotContain("parecerId");
    }

    @Test
    void propostaRejeitadaPorParecerIncluiParecerId() {
        UUID propostaId = UUID.randomUUID();
        UUID parecerId = UUID.randomUUID();

        listener.aoRejeitar(new PropostaRejeitadaEvent(propostaId, UUID.randomUUID(), OrigemDecisao.MANUAL, parecerId));

        ArgumentCaptor<String> detalhes = ArgumentCaptor.forClass(String.class);
        verify(auditService).gravar(eq(TipoEventoSeguranca.PROPOSTA_REJEITADA), any(UUID.class), detalhes.capture());
        assertThat(detalhes.getValue()).contains("\"origem\":\"MANUAL\"").contains(parecerId.toString());
    }

    private static UUID any(Class<UUID> cls) {
        return org.mockito.ArgumentMatchers.any(cls);
    }
}
