package com.dynamis.sep_api.backoffice.application.listener;

import com.dynamis.sep_api.backoffice.domain.event.ComentarioRegistradoEvent;
import com.dynamis.sep_api.backoffice.domain.event.ItemAssumidoEvent;
import com.dynamis.sep_api.backoffice.domain.event.ItemFilaCriadoEvent;
import com.dynamis.sep_api.backoffice.domain.event.ItemIgnoradoEvent;
import com.dynamis.sep_api.backoffice.domain.event.ItemResolvidoEvent;
import com.dynamis.sep_api.backoffice.domain.event.ReprocessoDisparadoEvent;
import com.dynamis.sep_api.backoffice.domain.model.Reprocesso;
import com.dynamis.sep_api.backoffice.domain.vo.PrioridadeItem;
import com.dynamis.sep_api.backoffice.domain.vo.TipoEntidadeReferenciada;
import com.dynamis.sep_api.backoffice.domain.vo.TipoItemFila;
import com.dynamis.sep_api.shared.audit.AuditLogSegurancaService;
import com.dynamis.sep_api.shared.audit.TipoEventoSeguranca;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class BackofficeAuditListenerTest {

    private AuditLogSegurancaService auditLog;
    private BackofficeAuditListener listener;

    @BeforeEach
    void setup() {
        auditLog = mock(AuditLogSegurancaService.class);
        listener = new BackofficeAuditListener(auditLog, new ObjectMapper());
    }

    @Test
    void aoCriarItem_gravaAuditComPayload() {
        UUID itemId = UUID.randomUUID();
        UUID entidadeId = UUID.randomUUID();
        listener.aoCriarItem(new ItemFilaCriadoEvent(
                itemId,
                TipoItemFila.ONBOARDING_ERRO,
                PrioridadeItem.ALTA,
                TipoEntidadeReferenciada.ONBOARDING,
                entidadeId));

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(auditLog)
                .gravar(
                        org.mockito.ArgumentMatchers.eq(TipoEventoSeguranca.ITEM_FILA_CRIADO),
                        org.mockito.ArgumentMatchers.isNull(),
                        payload.capture());
        assertThat(payload.getValue())
                .contains(itemId.toString())
                .contains("ONBOARDING_ERRO")
                .contains("ALTA");
    }

    @Test
    void aoAssumirItem_gravaAuditComOperadorEItemId() {
        UUID itemId = UUID.randomUUID();
        UUID operadorId = UUID.randomUUID();
        listener.aoAssumirItem(new ItemAssumidoEvent(itemId, operadorId, OffsetDateTime.now()));

        verify(auditLog)
                .gravar(
                        org.mockito.ArgumentMatchers.eq(TipoEventoSeguranca.ITEM_ASSUMIDO),
                        org.mockito.ArgumentMatchers.eq(operadorId),
                        org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void aoRegistrarComentario_naoVazaConteudoCompleto() {
        UUID itemId = UUID.randomUUID();
        UUID comentarioId = UUID.randomUUID();
        UUID autorId = UUID.randomUUID();
        String resumo = "obs operacional resumida (max 80 chars no evento)";
        listener.aoRegistrarComentario(new ComentarioRegistradoEvent(itemId, comentarioId, autorId, resumo));

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(auditLog)
                .gravar(
                        org.mockito.ArgumentMatchers.eq(TipoEventoSeguranca.COMENTARIO_REGISTRADO),
                        org.mockito.ArgumentMatchers.eq(autorId),
                        payload.capture());
        assertThat(payload.getValue()).contains(resumo);
    }

    @Test
    void aoResolverItem_gravaJustificativaResumida() {
        UUID itemId = UUID.randomUUID();
        UUID operadorId = UUID.randomUUID();
        String justificativa = "Documento validado manualmente apos contato";
        listener.aoResolverItem(new ItemResolvidoEvent(itemId, operadorId, justificativa));

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(auditLog)
                .gravar(
                        org.mockito.ArgumentMatchers.eq(TipoEventoSeguranca.ITEM_RESOLVIDO),
                        org.mockito.ArgumentMatchers.eq(operadorId),
                        payload.capture());
        assertThat(payload.getValue()).contains(justificativa);
    }

    @Test
    void aoIgnorarItem_gravaJustificativaResumida() {
        UUID itemId = UUID.randomUUID();
        UUID operadorId = UUID.randomUUID();
        listener.aoIgnorarItem(new ItemIgnoradoEvent(itemId, operadorId, "Item duplicado fluxo manual"));

        verify(auditLog)
                .gravar(
                        org.mockito.ArgumentMatchers.eq(TipoEventoSeguranca.ITEM_IGNORADO),
                        org.mockito.ArgumentMatchers.eq(operadorId),
                        org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void aoDispararReprocesso_gravaCamposCompletos() {
        UUID reprocessoId = UUID.randomUUID();
        UUID operadorId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        listener.aoDispararReprocesso(new ReprocessoDisparadoEvent(
                reprocessoId,
                Reprocesso.Tipo.PROVIDER,
                com.dynamis.sep_api.backoffice.domain.vo.TipoChamadaProvider.KYC,
                "entidade-id-X",
                com.dynamis.sep_api.backoffice.domain.vo.StatusReprocesso.SUCESSO,
                itemId,
                operadorId));

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(auditLog)
                .gravar(
                        org.mockito.ArgumentMatchers.eq(TipoEventoSeguranca.REPROCESSO_DISPARADO),
                        org.mockito.ArgumentMatchers.eq(operadorId),
                        payload.capture());
        assertThat(payload.getValue())
                .contains(reprocessoId.toString())
                .contains("PROVIDER")
                .contains("KYC")
                .contains("SUCESSO")
                .contains("entidade-id-X")
                .contains(itemId.toString());
    }

    @Test
    void aoDispararReprocesso_webhookSemTipoChamadaNemItem() {
        UUID reprocessoId = UUID.randomUUID();
        listener.aoDispararReprocesso(new ReprocessoDisparadoEvent(
                reprocessoId,
                Reprocesso.Tipo.WEBHOOK,
                null,
                "webhook-id-X",
                com.dynamis.sep_api.backoffice.domain.vo.StatusReprocesso.FALHA,
                null,
                UUID.randomUUID()));

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(auditLog)
                .gravar(
                        org.mockito.ArgumentMatchers.eq(TipoEventoSeguranca.REPROCESSO_DISPARADO),
                        org.mockito.ArgumentMatchers.any(),
                        payload.capture());
        assertThat(payload.getValue())
                .contains("WEBHOOK")
                .contains("FALHA")
                .doesNotContain("tipoChamada")
                .doesNotContain("itemId");
    }

    @Test
    void mascaraCpf_emConteudoComentario() {
        UUID itemId = UUID.randomUUID();
        UUID autorId = UUID.randomUUID();
        // Operador digitou CPF — listener deve mascarar antes do audit
        listener.aoRegistrarComentario(new ComentarioRegistradoEvent(
                itemId, UUID.randomUUID(), autorId, "Tomador 529.982.247-25 confirmou pagamento"));

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(auditLog)
                .gravar(
                        org.mockito.ArgumentMatchers.eq(TipoEventoSeguranca.COMENTARIO_REGISTRADO),
                        org.mockito.ArgumentMatchers.eq(autorId),
                        payload.capture());
        assertThat(payload.getValue()).doesNotContain("529.982.247-25").doesNotContain("52998224725");
        assertThat(payload.getValue()).contains("***.***.***-**");
    }

    @Test
    void mascaraCnpj_emJustificativaResolver() {
        UUID itemId = UUID.randomUUID();
        UUID operadorId = UUID.randomUUID();
        listener.aoResolverItem(
                new ItemResolvidoEvent(itemId, operadorId, "PJ 11.222.333/0001-81 confirmou recibo de pagamento"));

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(auditLog)
                .gravar(
                        org.mockito.ArgumentMatchers.eq(TipoEventoSeguranca.ITEM_RESOLVIDO),
                        org.mockito.ArgumentMatchers.eq(operadorId),
                        payload.capture());
        assertThat(payload.getValue()).doesNotContain("11.222.333/0001-81").doesNotContain("11222333000181");
        assertThat(payload.getValue()).contains("**.***.***/****-**");
    }

    @Test
    void falhaAuditService_naoPropaga() {
        // fix review manual Task 14.8: handler nao deve propagar excecao do audit service.
        org.mockito.Mockito.doThrow(new RuntimeException("DB down"))
                .when(auditLog)
                .gravar(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyString());

        listener.aoCriarItem(new ItemFilaCriadoEvent(
                UUID.randomUUID(),
                TipoItemFila.OUTRO,
                PrioridadeItem.BAIXA,
                TipoEntidadeReferenciada.OUTRO,
                UUID.randomUUID()));
        // se chegou aqui sem propagar, fluxo principal preservado
    }

    @Test
    void guardDefensivo_truncaResumoMesmoQuandoEventoVemMaior() {
        // fix review manual Task 14.8: defesa em profundidade. Se use case mudar e remover
        // truncamento, listener garante que audit ainda nao vaza dado completo.
        UUID itemId = UUID.randomUUID();
        UUID autorId = UUID.randomUUID();
        String resumoLongo = "x".repeat(200);
        listener.aoRegistrarComentario(new ComentarioRegistradoEvent(itemId, UUID.randomUUID(), autorId, resumoLongo));

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(auditLog)
                .gravar(
                        org.mockito.ArgumentMatchers.eq(TipoEventoSeguranca.COMENTARIO_REGISTRADO),
                        org.mockito.ArgumentMatchers.eq(autorId),
                        payload.capture());
        // Truncado em 80 chars + "..."; nao deve conter o "xxx...xxx" inteiro de 200 chars
        assertThat(payload.getValue()).doesNotContain("x".repeat(85));
        assertThat(payload.getValue()).contains("...");
    }

    @Test
    void payload_naoCarregaDadosSensiveis() {
        // Defesa de sanitizacao: nenhum campo do listener serializa CPF/CNPJ/telefone/token.
        // Eventos do dominio carregam apenas UUIDs + resumos truncados pelos use cases.
        UUID itemId = UUID.randomUUID();
        listener.aoCriarItem(new ItemFilaCriadoEvent(
                itemId,
                TipoItemFila.ONBOARDING_ERRO,
                PrioridadeItem.ALTA,
                TipoEntidadeReferenciada.ONBOARDING,
                UUID.randomUUID()));

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(auditLog)
                .gravar(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), payload.capture());
        String s = payload.getValue();
        assertThat(s).doesNotContain("cpf").doesNotContain("cnpj").doesNotContain("token");
    }
}
