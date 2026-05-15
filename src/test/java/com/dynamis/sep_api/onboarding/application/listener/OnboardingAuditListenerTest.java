package com.dynamis.sep_api.onboarding.application.listener;

import com.dynamis.sep_api.onboarding.domain.event.DocumentoCadastralEnviadoEvent;
import com.dynamis.sep_api.onboarding.domain.event.KybFinalizadoEvent;
import com.dynamis.sep_api.onboarding.domain.event.KybIniciadoEvent;
import com.dynamis.sep_api.onboarding.domain.event.OnboardingFinalizadoEvent;
import com.dynamis.sep_api.onboarding.domain.event.OnboardingIniciadoEvent;
import com.dynamis.sep_api.onboarding.domain.event.PldFinalizadoEvent;
import com.dynamis.sep_api.onboarding.domain.event.PldHitDetectadoEvent;
import com.dynamis.sep_api.onboarding.domain.event.PldIniciadoEvent;
import com.dynamis.sep_api.onboarding.domain.event.PldLimpoEvent;
import com.dynamis.sep_api.onboarding.domain.event.VerificacaoKycDisparadaEvent;
import com.dynamis.sep_api.onboarding.domain.vo.AlvoPld;
import com.dynamis.sep_api.onboarding.domain.vo.BasePld;
import com.dynamis.sep_api.onboarding.domain.vo.SeveridadePld;
import com.dynamis.sep_api.onboarding.domain.vo.StatusOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.TipoDocumento;
import com.dynamis.sep_api.shared.audit.AuditLogSegurancaService;
import com.dynamis.sep_api.shared.audit.TipoEventoSeguranca;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OnboardingAuditListenerTest {

    private AuditLogSegurancaService auditService;
    private OnboardingAuditListener listener;
    private ObjectMapper objectMapper;

    private UUID solicitacaoId;
    private UUID usuarioId;

    @BeforeEach
    void setup() {
        auditService = mock(AuditLogSegurancaService.class);
        objectMapper = new ObjectMapper();
        listener = new OnboardingAuditListener(auditService, objectMapper);
        solicitacaoId = UUID.randomUUID();
        usuarioId = UUID.randomUUID();
    }

    private String capturarDetalhes(TipoEventoSeguranca tipo) {
        ArgumentCaptor<String> detalhes = ArgumentCaptor.forClass(String.class);
        verify(auditService).gravar(eq(tipo), eq(usuarioId), detalhes.capture());
        return detalhes.getValue();
    }

    private String capturarDetalhesSemUsuario(TipoEventoSeguranca tipo) {
        ArgumentCaptor<String> detalhes = ArgumentCaptor.forClass(String.class);
        verify(auditService).gravar(eq(tipo), isNull(), detalhes.capture());
        return detalhes.getValue();
    }

    private JsonNode parsear(String json) throws Exception {
        return objectMapper.readTree(json);
    }

    @Test
    void onboardingIniciadoGravaKycIniciadoComSolicitacaoId() throws Exception {
        listener.aoIniciar(new OnboardingIniciadoEvent(solicitacaoId, usuarioId));

        JsonNode json = parsear(capturarDetalhes(TipoEventoSeguranca.KYC_INICIADO));
        assertThat(json.get("solicitacaoId").asText()).isEqualTo(solicitacaoId.toString());
    }

    @Test
    void documentoEnviadoGravaKycDocumentoEnviadoComMetadados() throws Exception {
        UUID documentoId = UUID.randomUUID();
        listener.aoEnviarDocumento(
                new DocumentoCadastralEnviadoEvent(solicitacaoId, usuarioId, documentoId, TipoDocumento.RG, "sha-abc"));

        JsonNode json = parsear(capturarDetalhes(TipoEventoSeguranca.KYC_DOCUMENTO_ENVIADO));
        assertThat(json.get("solicitacaoId").asText()).isEqualTo(solicitacaoId.toString());
        assertThat(json.get("documentoId").asText()).isEqualTo(documentoId.toString());
        assertThat(json.get("tipoDocumento").asText()).isEqualTo("RG");
        assertThat(json.get("sha256").asText()).isEqualTo("sha-abc");
    }

    @Test
    void verificacaoDisparadaGravaKycVerificacaoDisparadaComIdExterno() throws Exception {
        listener.aoDispararVerificacao(new VerificacaoKycDisparadaEvent(solicitacaoId, usuarioId, "ext-1"));

        JsonNode json = parsear(capturarDetalhes(TipoEventoSeguranca.KYC_VERIFICACAO_DISPARADA));
        assertThat(json.get("solicitacaoId").asText()).isEqualTo(solicitacaoId.toString());
        assertThat(json.get("idVerificacaoExterna").asText()).isEqualTo("ext-1");
    }

    @Test
    void finalizadoAprovadoMapeiaParaKycFinalizadoAprovado() throws Exception {
        listener.aoFinalizar(
                new OnboardingFinalizadoEvent(solicitacaoId, usuarioId, StatusOnboarding.APROVADO, "ext-1"));

        JsonNode json = parsear(capturarDetalhes(TipoEventoSeguranca.KYC_FINALIZADO_APROVADO));
        assertThat(json.get("statusFinal").asText()).isEqualTo("APROVADO");
    }

    @Test
    void finalizadoReprovadoMapeiaParaKycFinalizadoReprovado() throws Exception {
        listener.aoFinalizar(
                new OnboardingFinalizadoEvent(solicitacaoId, usuarioId, StatusOnboarding.REPROVADO, "ext-2"));

        JsonNode json = parsear(capturarDetalhes(TipoEventoSeguranca.KYC_FINALIZADO_REPROVADO));
        assertThat(json.get("statusFinal").asText()).isEqualTo("REPROVADO");
    }

    @Test
    void finalizadoPendenciaMapeiaParaKycFinalizadoPendencia() throws Exception {
        listener.aoFinalizar(
                new OnboardingFinalizadoEvent(solicitacaoId, usuarioId, StatusOnboarding.PENDENCIA, "ext-3"));

        JsonNode json = parsear(capturarDetalhes(TipoEventoSeguranca.KYC_FINALIZADO_PENDENCIA));
        assertThat(json.get("statusFinal").asText()).isEqualTo("PENDENCIA");
    }

    @Test
    void finalizadoComStatusNaoFinalRejeitadoSemGravarAuditLog() {
        OnboardingFinalizadoEvent invalido =
                new OnboardingFinalizadoEvent(solicitacaoId, usuarioId, StatusOnboarding.INICIADO, "ext-bad");

        assertThatThrownBy(() -> listener.aoFinalizar(invalido)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void kybIniciadoGravaKybIniciadoComCnpjMascarado() throws Exception {
        listener.aoIniciarKyb(new KybIniciadoEvent(solicitacaoId, usuarioId, "11222333000181"));

        JsonNode json = parsear(capturarDetalhes(TipoEventoSeguranca.KYB_INICIADO));
        assertThat(json.get("solicitacaoId").asText()).isEqualTo(solicitacaoId.toString());
        assertThat(json.get("cnpjMascarado").asText()).isEqualTo("112*********81");
        assertThat(json.has("cnpj")).isFalse();
    }

    @Test
    void kybFinalizadoAprovadoMapeiaParaKybFinalizadoAprovado() throws Exception {
        UUID kybId = UUID.randomUUID();
        listener.aoFinalizarKyb(new KybFinalizadoEvent(solicitacaoId, usuarioId, StatusOnboarding.APROVADO, kybId));

        JsonNode json = parsear(capturarDetalhes(TipoEventoSeguranca.KYB_FINALIZADO_APROVADO));
        assertThat(json.get("statusFinal").asText()).isEqualTo("APROVADO");
        assertThat(json.get("kybEmpresaId").asText()).isEqualTo(kybId.toString());
    }

    @Test
    void kybFinalizadoReprovadoMapeiaParaKybFinalizadoReprovado() throws Exception {
        listener.aoFinalizarKyb(
                new KybFinalizadoEvent(solicitacaoId, usuarioId, StatusOnboarding.REPROVADO, UUID.randomUUID()));

        JsonNode json = parsear(capturarDetalhes(TipoEventoSeguranca.KYB_FINALIZADO_REPROVADO));
        assertThat(json.get("statusFinal").asText()).isEqualTo("REPROVADO");
    }

    @Test
    void kybFinalizadoComStatusNaoSuportadoRejeitado() {
        KybFinalizadoEvent invalido =
                new KybFinalizadoEvent(solicitacaoId, usuarioId, StatusOnboarding.INICIADO, UUID.randomUUID());

        assertThatThrownBy(() -> listener.aoFinalizarKyb(invalido)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pldIniciadoGravaPldIniciadoSemUsuario() throws Exception {
        listener.aoIniciarPld(new PldIniciadoEvent(solicitacaoId, AlvoPld.EMPRESA, "112*********81"));

        JsonNode json = parsear(capturarDetalhesSemUsuario(TipoEventoSeguranca.PLD_INICIADO));
        assertThat(json.get("alvoTipo").asText()).isEqualTo("EMPRESA");
        assertThat(json.get("documentoMascarado").asText()).isEqualTo("112*********81");
    }

    @Test
    void pldHitDetectadoGravaPldHitDetectadoComBaseSeveridadeEMotivo() throws Exception {
        listener.aoDetectarHitPld(new PldHitDetectadoEvent(
                solicitacaoId, AlvoPld.REPRESENTANTE, BasePld.OFAC, SeveridadePld.ALTA, "SDN list - terrorism"));

        JsonNode json = parsear(capturarDetalhesSemUsuario(TipoEventoSeguranca.PLD_HIT_DETECTADO));
        assertThat(json.get("alvoTipo").asText()).isEqualTo("REPRESENTANTE");
        assertThat(json.get("base").asText()).isEqualTo("OFAC");
        assertThat(json.get("severidade").asText()).isEqualTo("ALTA");
        assertThat(json.get("motivo").asText()).isEqualTo("SDN list - terrorism");
        assertThat(json.has("payloadProvider")).isFalse();
    }

    @Test
    void pldHitDetectadoComSeveridadeNullaGravaSentinelNaoInformada() throws Exception {
        listener.aoDetectarHitPld(
                new PldHitDetectadoEvent(solicitacaoId, AlvoPld.PESSOA, BasePld.COAF, null, "lista PEP"));

        JsonNode json = parsear(capturarDetalhesSemUsuario(TipoEventoSeguranca.PLD_HIT_DETECTADO));
        assertThat(json.get("severidade").asText()).isEqualTo("NAO_INFORMADA");
        assertThat(json.get("motivo").asText()).isEqualTo("lista PEP");
    }

    @Test
    void pldHitDetectadoComMotivoNullGravaSentinelNaoInformado() throws Exception {
        listener.aoDetectarHitPld(
                new PldHitDetectadoEvent(solicitacaoId, AlvoPld.PESSOA, BasePld.MTE, SeveridadePld.MEDIA, null));

        JsonNode json = parsear(capturarDetalhesSemUsuario(TipoEventoSeguranca.PLD_HIT_DETECTADO));
        assertThat(json.get("motivo").asText()).isEqualTo("NAO_INFORMADO");
    }

    @Test
    void pldHitDetectadoComMotivoMuitoLongoEhTruncadoEm200Chars() throws Exception {
        String motivoLongo = "x".repeat(500);
        listener.aoDetectarHitPld(new PldHitDetectadoEvent(
                solicitacaoId, AlvoPld.PESSOA, BasePld.INTERPOL, SeveridadePld.BAIXA, motivoLongo));

        JsonNode json = parsear(capturarDetalhesSemUsuario(TipoEventoSeguranca.PLD_HIT_DETECTADO));
        assertThat(json.get("motivo").asText()).hasSize(200);
    }

    @Test
    void pldLimpoGravaPldLimpoSemUsuario() throws Exception {
        listener.aoLimparPld(new PldLimpoEvent(solicitacaoId, AlvoPld.PESSOA, "529******25"));

        JsonNode json = parsear(capturarDetalhesSemUsuario(TipoEventoSeguranca.PLD_LIMPO));
        assertThat(json.get("alvoTipo").asText()).isEqualTo("PESSOA");
        assertThat(json.get("documentoMascarado").asText()).isEqualTo("529******25");
    }

    @Test
    void pldFinalizadoAprovadoFinalGravaPldFinalizado() throws Exception {
        listener.aoFinalizarPld(new PldFinalizadoEvent(solicitacaoId, usuarioId, StatusOnboarding.APROVADO_FINAL));

        JsonNode json = parsear(capturarDetalhes(TipoEventoSeguranca.PLD_FINALIZADO));
        assertThat(json.get("statusFinal").asText()).isEqualTo("APROVADO_FINAL");
    }

    @Test
    void pldFinalizadoReprovadoGravaPldFinalizado() throws Exception {
        listener.aoFinalizarPld(new PldFinalizadoEvent(solicitacaoId, usuarioId, StatusOnboarding.REPROVADO_PLD));

        JsonNode json = parsear(capturarDetalhes(TipoEventoSeguranca.PLD_FINALIZADO));
        assertThat(json.get("statusFinal").asText()).isEqualTo("REPROVADO_PLD");
    }

    @Test
    void valoresExternosComAspasEBarrasSaoEscapadosNoJson() throws Exception {
        // idVerificacaoExterna vem do provider — deve ser escapado mesmo com aspas/barras.
        String idMalicioso = "ext\"with\\backslash\nand-control";
        listener.aoDispararVerificacao(new VerificacaoKycDisparadaEvent(solicitacaoId, usuarioId, idMalicioso));

        String raw = capturarDetalhes(TipoEventoSeguranca.KYC_VERIFICACAO_DISPARADA);
        // JSON ainda parseavel + valor preservado apos escape.
        JsonNode json = parsear(raw);
        assertThat(json.get("idVerificacaoExterna").asText()).isEqualTo(idMalicioso);
    }
}
