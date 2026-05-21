package com.dynamis.sep_api.contratos.application.listener;

import com.dynamis.sep_api.contratos.domain.event.AssinaturaEnviadaEvent;
import com.dynamis.sep_api.contratos.domain.event.AssinaturaVisualizadaEvent;
import com.dynamis.sep_api.contratos.domain.event.CcbGeradaEvent;
import com.dynamis.sep_api.contratos.domain.event.ContratoAceitoEvent;
import com.dynamis.sep_api.contratos.domain.event.ContratoAssinadoEvent;
import com.dynamis.sep_api.contratos.domain.event.ContratoCanceladoEvent;
import com.dynamis.sep_api.contratos.domain.event.ContratoGeradoEvent;
import com.dynamis.sep_api.contratos.domain.event.ContratoNovaVersaoEvent;
import com.dynamis.sep_api.contratos.domain.event.ContratoRecusadoEvent;
import com.dynamis.sep_api.contratos.domain.event.DocumentoAssinadoBaixadoEvent;
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

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

        // IP e user-agent ficam APENAS nas colunas dedicadas (3o e 4o args); nao duplicam no JSON
        verify(auditLogService)
                .gravar(
                        eq(TipoEventoSeguranca.CONTRATO_ACEITO),
                        eq(tomadorId),
                        eq("203.0.113.42"),
                        eq("Mozilla/5.0"),
                        org.mockito.ArgumentMatchers.argThat(
                                (String json) -> json.contains("\"hashSha256\":\"" + HASH + "\"")
                                        && !json.contains("userAgentOrigem")
                                        && !json.contains("ipOrigem")));
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

    // ============== Sprint 11 Task 11.8: ciclo de assinatura digital ==============

    @Test
    void aoGerarCcb_gravaComHashPdfGerado() {
        UUID contratoId = UUID.randomUUID();
        UUID tomadorId = UUID.randomUUID();
        String hashPdf = "a".repeat(64);

        listener.aoGerarCcb(
                new CcbGeradaEvent(contratoId, UUID.randomUUID(), tomadorId, UUID.randomUUID(), 1, hashPdf));

        verify(auditLogService)
                .gravar(
                        eq(TipoEventoSeguranca.CCB_GERADA),
                        eq(tomadorId),
                        matches(".*\"contratoId\":\"" + contratoId + "\".*\"hashPdfGerado\":\"" + hashPdf + "\".*"));
    }

    @Test
    void aoEnviarAssinatura_gravaComIdEnvelopeExternoEHash() {
        UUID contratoId = UUID.randomUUID();
        UUID tomadorId = UUID.randomUUID();
        UUID envelopeId = UUID.randomUUID();
        String hashPdf = "b".repeat(64);

        listener.aoEnviarAssinatura(new AssinaturaEnviadaEvent(
                contratoId,
                UUID.randomUUID(),
                tomadorId,
                UUID.randomUUID(),
                envelopeId,
                "ext-123",
                "clicksign",
                hashPdf));

        verify(auditLogService)
                .gravar(
                        eq(TipoEventoSeguranca.ASSINATURA_ENVIADA),
                        eq(tomadorId),
                        matches(".*\"envelopeId\":\"" + envelopeId
                                + "\".*\"idEnvelopeExterno\":\"ext-123\".*\"provider\":\"clicksign\".*\"hashPdfEnviado\":\""
                                + hashPdf + "\".*"));
    }

    @Test
    void aoVisualizarAssinatura_gravaInformativo() {
        UUID contratoId = UUID.randomUUID();
        UUID tomadorId = UUID.randomUUID();
        UUID envelopeId = UUID.randomUUID();

        listener.aoVisualizarAssinatura(new AssinaturaVisualizadaEvent(
                contratoId, tomadorId, envelopeId, "clicksign", OffsetDateTime.parse("2026-05-21T12:00:00Z")));

        verify(auditLogService)
                .gravar(
                        eq(TipoEventoSeguranca.ASSINATURA_VISUALIZADA),
                        eq(tomadorId),
                        matches(".*\"envelopeId\":\"" + envelopeId + "\".*\"provider\":\"clicksign\".*"));
    }

    @Test
    void aoAssinar_gravaComHashPdfAssinadoDocumentoIdEDataAssinatura() {
        UUID contratoId = UUID.randomUUID();
        UUID tomadorId = UUID.randomUUID();
        UUID envelopeId = UUID.randomUUID();
        UUID documentoId = UUID.randomUUID();
        String hashAssinado = "c".repeat(64);
        OffsetDateTime dataAssinatura = OffsetDateTime.parse("2026-05-21T15:30:00Z");

        listener.aoAssinar(new ContratoAssinadoEvent(
                contratoId,
                UUID.randomUUID(),
                tomadorId,
                UUID.randomUUID(),
                envelopeId,
                documentoId,
                hashAssinado,
                dataAssinatura));

        verify(auditLogService)
                .gravar(
                        eq(TipoEventoSeguranca.ASSINATURA_ASSINADA),
                        eq(tomadorId),
                        matches(".*\"envelopeId\":\"" + envelopeId + "\".*\"documentoAssinadoId\":\"" + documentoId
                                + "\".*\"hashPdfAssinado\":\"" + hashAssinado
                                + "\".*\"dataAssinatura\":\"2026-05-21T15:30Z\".*"));
    }

    @Test
    void aoRecusar_gravaAssinaturaRecusada() {
        UUID contratoId = UUID.randomUUID();
        UUID tomadorId = UUID.randomUUID();
        UUID envelopeId = UUID.randomUUID();

        listener.aoRecusar(
                new ContratoRecusadoEvent(contratoId, UUID.randomUUID(), tomadorId, UUID.randomUUID(), envelopeId));

        verify(auditLogService)
                .gravar(
                        eq(TipoEventoSeguranca.ASSINATURA_RECUSADA),
                        eq(tomadorId),
                        matches(".*\"envelopeId\":\"" + envelopeId + "\".*"));
    }

    @Test
    void aoBaixarDocumentoAssinado_gravaIpUserAgentEmColunasDedicadas() {
        UUID contratoId = UUID.randomUUID();
        UUID baixadoPorId = UUID.randomUUID();
        UUID envelopeId = UUID.randomUUID();
        UUID documentoId = UUID.randomUUID();

        listener.aoBaixarDocumentoAssinado(new DocumentoAssinadoBaixadoEvent(
                contratoId, envelopeId, documentoId, baixadoPorId, "198.51.100.7", "curl/8.5"));

        // ip + user-agent vivem em colunas dedicadas (3o e 4o args); JSONB sem PII
        verify(auditLogService)
                .gravar(
                        eq(TipoEventoSeguranca.DOCUMENTO_ASSINADO_BAIXADO),
                        eq(baixadoPorId),
                        eq("198.51.100.7"),
                        eq("curl/8.5"),
                        org.mockito.ArgumentMatchers.argThat(
                                (String json) -> json.contains("\"documentoAssinadoId\":\"" + documentoId + "\"")
                                        && !json.contains("ipOrigem")
                                        && !json.contains("userAgentOrigem")));
    }

    @Test
    void assinaturaEventos_naoVazamConteudoIntegralOuPdf() {
        // Defesa em profundidade: nenhum dos novos eventos pode carregar conteudo PDF, CCB
        // completa ou conteudoTexto de versao
        listener.aoGerarCcb(new CcbGeradaEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1, HASH));
        listener.aoEnviarAssinatura(new AssinaturaEnviadaEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "ext-1",
                "clicksign",
                HASH));
        listener.aoAssinar(new ContratoAssinadoEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                HASH,
                OffsetDateTime.now()));

        // Bloqueia conteudo textual + assinatura PDF binaria (header %PDF) + base64 PDF
        // (JVByR... eh os primeiros chars de "%PDF" em base64) — defesa em profundidade contra
        // regressao que envie bytes do documento no payload.
        verify(auditLogService, times(3))
                .gravar(
                        any(),
                        any(),
                        org.mockito.ArgumentMatchers.argThat((String json) -> !json.contains("conteudoTexto")
                                && !json.contains("clausulas")
                                && !json.contains("pdf")
                                && !json.contains("PDF")
                                && !json.contains("%PDF")
                                && !json.contains("JVByR")));
    }

    @Test
    void serializacaoFalhando_devolveFallbackComContratoId() throws Exception {
        // Mock ObjectMapper que joga JsonProcessingException — fallback deve preservar
        // rastreabilidade via contratoId em vez de retornar "{}"
        ObjectMapper omQuebrado = org.mockito.Mockito.mock(ObjectMapper.class);
        when(omQuebrado.writeValueAsString(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new com.fasterxml.jackson.core.JsonGenerationException("fake"));
        ContratoAuditListener quebrado = new ContratoAuditListener(auditLogService, omQuebrado);
        UUID contratoId = UUID.randomUUID();
        UUID tomadorId = UUID.randomUUID();

        quebrado.aoGerar(new ContratoGeradoEvent(contratoId, UUID.randomUUID(), tomadorId, UUID.randomUUID(), 1, HASH));

        verify(auditLogService)
                .gravar(
                        eq(TipoEventoSeguranca.CONTRATO_GERADO),
                        eq(tomadorId),
                        contains("\"contratoId\":\"" + contratoId + "\",\"erroSerializacao\":true"));
    }
}
