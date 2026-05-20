package com.dynamis.sep_api.contratos.application.listener;

import com.dynamis.sep_api.contratos.domain.event.ContratoAceitoEvent;
import com.dynamis.sep_api.contratos.domain.event.ContratoCanceladoEvent;
import com.dynamis.sep_api.contratos.domain.event.ContratoGeradoEvent;
import com.dynamis.sep_api.contratos.domain.event.ContratoNovaVersaoEvent;
import com.dynamis.sep_api.shared.audit.AuditLogSegurancaService;
import com.dynamis.sep_api.shared.audit.TipoEventoSeguranca;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ContratoAuditListenerTest {

    private static final String HASH = "0".repeat(64);

    @Mock
    private AuditLogSegurancaService auditLogService;

    private ContratoAuditListener listener;

    @BeforeEach
    void setup() {
        listener = new ContratoAuditListener(auditLogService, new ObjectMapper());
    }

    @Test
    void aoGerar_gravaContratoGeradoComPayload() {
        UUID contratoId = UUID.randomUUID();
        UUID propostaId = UUID.randomUUID();
        UUID tomadorId = UUID.randomUUID();
        UUID versaoId = UUID.randomUUID();

        listener.aoGerar(new ContratoGeradoEvent(contratoId, propostaId, tomadorId, versaoId, 1, HASH));

        verify(auditLogService)
                .gravar(
                        eq(TipoEventoSeguranca.CONTRATO_GERADO),
                        eq(tomadorId),
                        matches(".*\"contratoId\":\"" + contratoId + "\".*\"numeroVersao\":1.*\"hashSha256\":\"" + HASH
                                + "\".*"));
    }

    @Test
    void aoCriarNovaVersao_gravaContratoNovaVersao() {
        UUID contratoId = UUID.randomUUID();
        UUID tomadorId = UUID.randomUUID();

        listener.aoCriarNovaVersao(
                new ContratoNovaVersaoEvent(contratoId, UUID.randomUUID(), tomadorId, UUID.randomUUID(), 3, HASH));

        verify(auditLogService)
                .gravar(eq(TipoEventoSeguranca.CONTRATO_NOVA_VERSAO), eq(tomadorId), contains("\"numeroVersao\":3"));
    }

    @Test
    void aoAceitar_gravaContratoAceitoComIpUserAgentEHash() {
        UUID contratoId = UUID.randomUUID();
        UUID tomadorId = UUID.randomUUID();
        UUID versaoId = UUID.randomUUID();

        listener.aoAceitar(new ContratoAceitoEvent(
                contratoId, UUID.randomUUID(), tomadorId, versaoId, 1, HASH, "203.0.113.42", "Mozilla/5.0"));

        verify(auditLogService)
                .gravar(
                        eq(TipoEventoSeguranca.CONTRATO_ACEITO),
                        eq(tomadorId),
                        eq("203.0.113.42"),
                        eq("Mozilla/5.0"),
                        matches(".*\"hashSha256\":\"" + HASH + "\".*\"userAgentOrigem\":\"Mozilla/5.0\".*"));
    }

    @Test
    void aoCancelar_gravaContratoCanceladoComJustificativaTruncada() {
        UUID contratoId = UUID.randomUUID();
        UUID canceladoPorId = UUID.randomUUID();
        String justificativaGigante = "x".repeat(300);

        listener.aoCancelar(new ContratoCanceladoEvent(
                contratoId, UUID.randomUUID(), UUID.randomUUID(), canceladoPorId, justificativaGigante));

        verify(auditLogService)
                .gravar(
                        eq(TipoEventoSeguranca.CONTRATO_CANCELADO),
                        eq(canceladoPorId),
                        matches(".*\"justificativa\":\"x{200}\".*"));
    }

    @Test
    void aoCancelar_justificativaCurtaNaoTrunca() {
        UUID canceladoPorId = UUID.randomUUID();
        String justificativa = "Cancelado por divergencia operacional antes do aceite.";

        listener.aoCancelar(new ContratoCanceladoEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), canceladoPorId, justificativa));

        verify(auditLogService, times(1))
                .gravar(
                        eq(TipoEventoSeguranca.CONTRATO_CANCELADO),
                        eq(canceladoPorId),
                        contains("\"justificativa\":\"Cancelado por divergencia operacional antes do aceite.\""));
    }

    @Test
    void aoAceitar_conteudoIntegralDoContratoNaoVazaParaAudit() {
        // Defesa em profundidade: o evento nao carrega conteudoTexto, mas validamos que o
        // payload gerado nunca o referencia (em caso de regressao futura)
        listener.aoAceitar(new ContratoAceitoEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1, HASH, "ip", "ua"));

        verify(auditLogService)
                .gravar(
                        any(),
                        any(),
                        any(),
                        any(),
                        org.mockito.ArgumentMatchers.argThat(
                                (String json) -> !json.contains("conteudoTexto") && !json.contains("clausulas")));
    }
}
