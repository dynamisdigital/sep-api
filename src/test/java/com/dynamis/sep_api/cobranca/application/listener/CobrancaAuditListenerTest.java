package com.dynamis.sep_api.cobranca.application.listener;

import com.dynamis.sep_api.cobranca.domain.event.AgendaGeradaEvent;
import com.dynamis.sep_api.cobranca.domain.event.EventoCobrancaRegistradoEvent;
import com.dynamis.sep_api.cobranca.domain.event.ParcelaAtrasouEvent;
import com.dynamis.sep_api.cobranca.domain.event.ParcelaInadimplenteEvent;
import com.dynamis.sep_api.cobranca.domain.event.ParcelaPagaEvent;
import com.dynamis.sep_api.cobranca.domain.event.RecebimentoRegistradoEvent;
import com.dynamis.sep_api.cobranca.domain.event.RenegociacaoAceitaEvent;
import com.dynamis.sep_api.cobranca.domain.event.RenegociacaoExpiradaEvent;
import com.dynamis.sep_api.cobranca.domain.event.RenegociacaoPropostaEvent;
import com.dynamis.sep_api.cobranca.domain.event.RenegociacaoRecusadaEvent;
import com.dynamis.sep_api.cobranca.domain.model.AgendaPagamento;
import com.dynamis.sep_api.cobranca.domain.model.AgendaPagamento.ParcelaPlanejada;
import com.dynamis.sep_api.cobranca.domain.vo.ComposicaoValor;
import com.dynamis.sep_api.cobranca.domain.vo.StatusParcela;
import com.dynamis.sep_api.cobranca.domain.vo.TipoEventoCobranca;
import com.dynamis.sep_api.cobranca.infrastructure.persistence.AgendaPagamentoRepository;
import com.dynamis.sep_api.escrow.domain.event.MovimentacaoEscrowCriadaEvent;
import com.dynamis.sep_api.shared.audit.AuditLogSegurancaService;
import com.dynamis.sep_api.shared.audit.TipoEventoSeguranca;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CobrancaAuditListenerTest {

    private AuditLogSegurancaService auditLogService;
    private AgendaPagamentoRepository agendaRepository;
    private CobrancaAuditListener listener;

    @BeforeEach
    void setup() {
        auditLogService = mock(AuditLogSegurancaService.class);
        agendaRepository = mock(AgendaPagamentoRepository.class);
        listener = new CobrancaAuditListener(auditLogService, agendaRepository, new ObjectMapper());
    }

    @Test
    void agendaGerada_gravaAgendaEParcelas() {
        AgendaPagamento agenda = AgendaPagamento.criar(
                UUID.randomUUID(),
                List.of(
                        new ParcelaPlanejada(
                                1, ComposicaoValor.principalApenas(new BigDecimal("100.00")), LocalDate.of(2026, 6, 1)),
                        new ParcelaPlanejada(
                                2,
                                ComposicaoValor.principalApenas(new BigDecimal("100.00")),
                                LocalDate.of(2026, 7, 1))));
        when(agendaRepository.findById(agenda.getId())).thenReturn(Optional.of(agenda));

        listener.aoGerarAgenda(new AgendaGeradaEvent(
                agenda.getId(),
                agenda.getContratoId(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                2,
                agenda.getValorTotal(),
                OffsetDateTime.now()));

        verify(auditLogService).gravar(eq(TipoEventoSeguranca.AGENDA_GERADA), any(), any(String.class));
        verify(auditLogService, times(2)).gravar(eq(TipoEventoSeguranca.PARCELA_CRIADA), any(), any(String.class));
    }

    @Test
    void recebimentoRegistrado_gravaAudit() {
        listener.aoRegistrarRecebimento(new RecebimentoRegistradoEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("100.00"),
                OffsetDateTime.now(),
                "TRANSFERENCIA",
                UUID.randomUUID()));

        verify(auditLogService).gravar(eq(TipoEventoSeguranca.RECEBIMENTO_REGISTRADO), any(), any(String.class));
    }

    @Test
    void parcelaPaga_gravaAudit() {
        listener.aoPagarParcela(new ParcelaPagaEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1, new BigDecimal("100.00")));

        verify(auditLogService).gravar(eq(TipoEventoSeguranca.PARCELA_PAGA), eq(null), any(String.class));
    }

    @Test
    void parcelaAtrasada_gravaAudit() {
        listener.aoAtrasarParcela(new ParcelaAtrasouEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1, LocalDate.of(2026, 5, 1)));

        verify(auditLogService).gravar(eq(TipoEventoSeguranca.PARCELA_ATRASADA), eq(null), any(String.class));
    }

    @Test
    void movimentacaoEscrowCriada_gravaAudit() {
        listener.aoCriarMovimentacaoEscrow(new MovimentacaoEscrowCriadaEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("100.00"),
                "Recebimento",
                OffsetDateTime.now(),
                UUID.randomUUID()));

        verify(auditLogService).gravar(eq(TipoEventoSeguranca.MOVIMENTACAO_ESCROW_CRIADA), eq(null), any(String.class));
    }

    @Test
    void payload_naoVazaDadosBancariosOuPessoais() {
        listener.aoRegistrarRecebimento(new RecebimentoRegistradoEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("100.00"),
                OffsetDateTime.now(),
                "TRANSFERENCIA",
                UUID.randomUUID()));

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditLogService).gravar(eq(TipoEventoSeguranca.RECEBIMENTO_REGISTRADO), any(), jsonCaptor.capture());
        String payload = jsonCaptor.getValue();
        assertThat(payload).doesNotContain("agencia", "conta", "cpf", "cnpj", "documento");
    }

    // ============================================================================
    // Sprint 13 Task 13.8 — auditoria reforcada inadimplencia + renegociacao
    // ============================================================================

    @Test
    void eventoCobrancaRegistrado_contatoManual_gravaAuditoria() {
        UUID financeiroId = UUID.randomUUID();
        listener.aoRegistrarEventoCobranca(new EventoCobrancaRegistradoEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                TipoEventoCobranca.CONTATO_MANUAL,
                com.dynamis.sep_api.cobranca.domain.vo.StatusEventoCobranca.SUCESSO,
                null,
                null,
                30,
                financeiroId));

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(auditLogService)
                .gravar(eq(TipoEventoSeguranca.EVENTO_COBRANCA_REGISTRADO), eq(financeiroId), json.capture());
        assertThat(json.getValue()).contains("CONTATO_MANUAL", "diasAtraso", "SUCESSO");
        assertThat(json.getValue()).doesNotContain("cpf", "cnpj", "telefone", "email", "agencia", "conta", "token");
    }

    @Test
    void parcelaInadimplente_actorNullPorqueEhJob() {
        // Fix code review Task 13.8: PARCELA_INADIMPLENTE eh disparada por job, nao por usuario.
        // actor=null preserva accountability; tomadorId vai pro payload pra rastreio.
        UUID tomadorId = UUID.randomUUID();
        listener.aoMarcarInadimplente(new ParcelaInadimplenteEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), tomadorId, 3, LocalDate.of(2026, 3, 15), 97));

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(auditLogService).gravar(eq(TipoEventoSeguranca.PARCELA_INADIMPLENTE), eq(null), json.capture());
        assertThat(json.getValue()).contains("diasAtraso", "97", tomadorId.toString());
        assertThat(json.getValue()).doesNotContain("cpf", "cnpj", "telefone", "email", "agencia");
    }

    @Test
    void eventoCobranca_notificacaoAutomaticaSucesso_mapeiaParaNotificacaoEnviadaComStatus() {
        // Fix code review Task 13.8: tipo NOTIFICACAO_ENVIADA estava orfo. Handler discrimina
        // NOTIFICACAO_AUTOMATICA -> NOTIFICACAO_ENVIADA + carrega status/canal/template no payload.
        listener.aoRegistrarEventoCobranca(new EventoCobrancaRegistradoEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                TipoEventoCobranca.NOTIFICACAO_AUTOMATICA,
                com.dynamis.sep_api.cobranca.domain.vo.StatusEventoCobranca.SUCESSO,
                com.dynamis.sep_api.cobranca.domain.vo.CanalNotificacao.EMAIL,
                "cobranca-amigavel",
                5,
                null));

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(auditLogService).gravar(eq(TipoEventoSeguranca.NOTIFICACAO_ENVIADA), eq(null), json.capture());
        assertThat(json.getValue()).contains("SUCESSO", "EMAIL", "cobranca-amigavel");
        assertThat(json.getValue()).doesNotContain("cpf", "cnpj", "telefone", "email\":", "agencia", "conta");
    }

    @Test
    void eventoCobranca_notificacaoFalha_auditaComoFALHA() {
        // Fix review manual Task 13.8: tentativa que nao chegou ao tomador precisa auditar.
        listener.aoRegistrarEventoCobranca(new EventoCobrancaRegistradoEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                TipoEventoCobranca.NOTIFICACAO_AUTOMATICA,
                com.dynamis.sep_api.cobranca.domain.vo.StatusEventoCobranca.FALHA,
                com.dynamis.sep_api.cobranca.domain.vo.CanalNotificacao.SMS,
                "cobranca-firme",
                15,
                null));

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(auditLogService).gravar(eq(TipoEventoSeguranca.NOTIFICACAO_ENVIADA), eq(null), json.capture());
        assertThat(json.getValue()).contains("FALHA", "SMS", "cobranca-firme");
    }

    @Test
    void renegociacaoProposta_naoVazaJustificativa() {
        UUID financeiroId = UUID.randomUUID();
        listener.aoProporRenegociacao(
                new RenegociacaoPropostaEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), financeiroId));

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(auditLogService).gravar(eq(TipoEventoSeguranca.RENEGOCIACAO_PROPOSTA), eq(financeiroId), json.capture());
        // justificativa NAO entra no payload (poderia conter texto livre com PII)
        assertThat(json.getValue()).doesNotContain("justificativa", "cpf", "cnpj", "novoValor", "desconto");
    }

    @Test
    void renegociacaoAceita_gravaAgendaSubstituta() {
        UUID tomadorId = UUID.randomUUID();
        UUID substitutaId = UUID.randomUUID();
        listener.aoAceitarRenegociacao(new RenegociacaoAceitaEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), substitutaId, tomadorId));

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(auditLogService).gravar(eq(TipoEventoSeguranca.RENEGOCIACAO_ACEITA), eq(tomadorId), json.capture());
        assertThat(json.getValue()).contains(substitutaId.toString());
        assertThat(json.getValue()).doesNotContain("cpf", "cnpj", "telefone", "email");
    }

    @Test
    void renegociacaoRecusada_gravaStatusRevertido() {
        UUID tomadorId = UUID.randomUUID();
        listener.aoRecusarRenegociacao(new RenegociacaoRecusadaEvent(
                UUID.randomUUID(), UUID.randomUUID(), tomadorId, StatusParcela.INADIMPLENTE));

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(auditLogService).gravar(eq(TipoEventoSeguranca.RENEGOCIACAO_RECUSADA), eq(tomadorId), json.capture());
        assertThat(json.getValue()).contains("INADIMPLENTE");
        assertThat(json.getValue()).doesNotContain("cpf", "cnpj");
    }

    @Test
    void renegociacaoExpirada_actorNull() {
        listener.aoExpirarRenegociacao(new RenegociacaoExpiradaEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), StatusParcela.ATRASADA));

        verify(auditLogService).gravar(eq(TipoEventoSeguranca.RENEGOCIACAO_EXPIRADA), eq(null), any(String.class));
    }
}
