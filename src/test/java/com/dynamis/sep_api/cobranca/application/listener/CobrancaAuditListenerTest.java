package com.dynamis.sep_api.cobranca.application.listener;

import com.dynamis.sep_api.cobranca.domain.event.AgendaGeradaEvent;
import com.dynamis.sep_api.cobranca.domain.event.ParcelaAtrasouEvent;
import com.dynamis.sep_api.cobranca.domain.event.ParcelaPagaEvent;
import com.dynamis.sep_api.cobranca.domain.event.RecebimentoRegistradoEvent;
import com.dynamis.sep_api.cobranca.domain.model.AgendaPagamento;
import com.dynamis.sep_api.cobranca.domain.model.AgendaPagamento.ParcelaPlanejada;
import com.dynamis.sep_api.cobranca.domain.vo.ComposicaoValor;
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
}
