package com.dynamis.sep_api.cobranca.application.usecase;

import com.dynamis.sep_api.cobranca.application.dto.RegistrarRecebimentoCommand;
import com.dynamis.sep_api.cobranca.application.dto.RegistrarRecebimentoResult;
import com.dynamis.sep_api.cobranca.application.port.out.ContratoCobrancaQueryPort;
import com.dynamis.sep_api.cobranca.application.port.out.RegistrarMovimentacaoEscrowPort;
import com.dynamis.sep_api.cobranca.application.port.out.RegistrarMovimentacaoEscrowPort.MovimentacaoEscrowResult;
import com.dynamis.sep_api.cobranca.domain.event.ParcelaPagaEvent;
import com.dynamis.sep_api.cobranca.domain.event.RecebimentoRegistradoEvent;
import com.dynamis.sep_api.cobranca.domain.exception.ChaveIdempotenciaConflitanteException;
import com.dynamis.sep_api.cobranca.domain.exception.ParcelaCobrancaNaoEncontradaException;
import com.dynamis.sep_api.cobranca.domain.exception.ParcelaEstadoInvalidoException;
import com.dynamis.sep_api.cobranca.domain.model.AgendaPagamento;
import com.dynamis.sep_api.cobranca.domain.model.AgendaPagamento.ParcelaPlanejada;
import com.dynamis.sep_api.cobranca.domain.model.ParcelaCobranca;
import com.dynamis.sep_api.cobranca.domain.model.Recebimento;
import com.dynamis.sep_api.cobranca.domain.vo.ComposicaoValor;
import com.dynamis.sep_api.cobranca.domain.vo.StatusParcela;
import com.dynamis.sep_api.cobranca.infrastructure.persistence.ParcelaCobrancaRepository;
import com.dynamis.sep_api.cobranca.infrastructure.persistence.RecebimentoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegistrarRecebimentoUseCaseTest {

    private ParcelaCobrancaRepository parcelaRepository;
    private RecebimentoRepository recebimentoRepository;
    private RegistrarMovimentacaoEscrowPort escrowPort;
    private ContratoCobrancaQueryPort contratoQueryPort;
    private ApplicationEventPublisher eventPublisher;
    private RegistrarRecebimentoUseCase useCase;

    private static final UUID OPERADOR_ID = UUID.randomUUID();
    private static final String KEY = "key-test-001";

    @BeforeEach
    void setup() {
        parcelaRepository = mock(ParcelaCobrancaRepository.class);
        recebimentoRepository = mock(RecebimentoRepository.class);
        escrowPort = mock(RegistrarMovimentacaoEscrowPort.class);
        contratoQueryPort = mock(ContratoCobrancaQueryPort.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        useCase = new RegistrarRecebimentoUseCase(
                parcelaRepository, recebimentoRepository, escrowPort, contratoQueryPort, eventPublisher);
    }

    @Test
    void pagamentoTotal_marcaPagaEPublicaEventos() {
        ParcelaCobranca parcela = novaParcela("100.00");
        UUID contratoId = parcela.getAgenda().getContratoId();
        UUID propostaId = UUID.randomUUID();
        mockarFluxoNovo(parcela, contratoId, propostaId);

        RegistrarRecebimentoResult r = useCase.executar(comando(parcela.getId(), new BigDecimal("100.00"), KEY));

        assertThat(r.novo()).isTrue();
        assertThat(r.statusParcela()).isEqualTo(StatusParcela.PAGA);
        assertThat(r.valorRecebido()).isEqualByComparingTo("100.00");
        verify(eventPublisher).publishEvent(any(RecebimentoRegistradoEvent.class));
        verify(eventPublisher).publishEvent(any(ParcelaPagaEvent.class));
        verify(escrowPort).registrarRecebimento(eq(propostaId), any(), eq(KEY), any(), any(UUID.class));
    }

    @Test
    void pagamentoParcial_marcaParcialmenteEnaoPublicaPagaEvent() {
        ParcelaCobranca parcela = novaParcela("100.00");
        UUID contratoId = parcela.getAgenda().getContratoId();
        UUID propostaId = UUID.randomUUID();
        mockarFluxoNovo(parcela, contratoId, propostaId);

        RegistrarRecebimentoResult r = useCase.executar(comando(parcela.getId(), new BigDecimal("40.00"), KEY));

        assertThat(r.statusParcela()).isEqualTo(StatusParcela.PARCIALMENTE_PAGA);
        verify(eventPublisher).publishEvent(any(RecebimentoRegistradoEvent.class));
        verify(eventPublisher, never()).publishEvent(any(ParcelaPagaEvent.class));
    }

    @Test
    void overpayment_marcaPagaEReportaTotalRecebido() {
        ParcelaCobranca parcela = novaParcela("100.00");
        UUID contratoId = parcela.getAgenda().getContratoId();
        UUID propostaId = UUID.randomUUID();
        mockarFluxoNovo(parcela, contratoId, propostaId);

        RegistrarRecebimentoResult r = useCase.executar(comando(parcela.getId(), new BigDecimal("150.00"), KEY));

        assertThat(r.statusParcela()).isEqualTo(StatusParcela.PAGA);
        ArgumentCaptor<ParcelaPagaEvent> captor = ArgumentCaptor.forClass(ParcelaPagaEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().totalRecebido()).isEqualByComparingTo("150.00");
    }

    @Test
    void idempotente_chaveExistente_naoCriaNovoNemRepublicaEvento() {
        ParcelaCobranca parcela = novaParcela("100.00");
        // Aplica o recebimento direto na entidade pra simular estado pos-persist.
        Recebimento existente = parcela.registrarRecebimento(
                new BigDecimal("100.00"),
                parcela.valorTotal(),
                OffsetDateTime.now(),
                "TRANSFERENCIA",
                "comp-1",
                KEY,
                null,
                OPERADOR_ID);
        when(recebimentoRepository.findByIdempotencyKey(KEY)).thenReturn(Optional.of(existente));
        when(contratoQueryPort.propostaIdDoContrato(parcela.getAgenda().getContratoId()))
                .thenReturn(Optional.of(UUID.randomUUID()));
        when(escrowPort.registrarRecebimento(any(), any(), eq(KEY), any(), any(UUID.class)))
                .thenReturn(
                        new MovimentacaoEscrowResult(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("100.00")));

        RegistrarRecebimentoResult r = useCase.executar(comando(parcela.getId(), new BigDecimal("100.00"), KEY));

        assertThat(r.novo()).isFalse();
        assertThat(r.recebimentoId()).isEqualTo(existente.getId());
        verify(parcelaRepository, never()).findByIdForUpdate(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void parcelaJaPaga_segundaChaveDistinta_rejeita() {
        ParcelaCobranca parcela = novaParcela("100.00");
        parcela.registrarRecebimento(
                new BigDecimal("100.00"),
                parcela.valorTotal(),
                OffsetDateTime.now(),
                "TRANSFERENCIA",
                null,
                "key-anterior",
                null,
                OPERADOR_ID);
        when(recebimentoRepository.findByIdempotencyKey(KEY)).thenReturn(Optional.empty());
        when(parcelaRepository.findByIdForUpdate(parcela.getId())).thenReturn(Optional.of(parcela));

        assertThatThrownBy(() -> useCase.executar(comando(parcela.getId(), new BigDecimal("10.00"), KEY)))
                .isInstanceOf(ParcelaEstadoInvalidoException.class);
        verify(escrowPort, never()).registrarRecebimento(any(), any(), any(), any(), any());
    }

    @Test
    void parcelaInexistente_lancaExcecao() {
        UUID parcelaId = UUID.randomUUID();
        when(recebimentoRepository.findByIdempotencyKey(KEY)).thenReturn(Optional.empty());
        when(parcelaRepository.findByIdForUpdate(parcelaId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(comando(parcelaId, new BigDecimal("10.00"), KEY)))
                .isInstanceOf(ParcelaCobrancaNaoEncontradaException.class);
    }

    @Test
    void idempotente_keyComParcelaDiferente_lancaConflito() {
        ParcelaCobranca parcela = novaParcela("100.00");
        ParcelaCobranca outraParcela = novaParcela("100.00");
        Recebimento existente = parcela.registrarRecebimento(
                new BigDecimal("100.00"),
                parcela.valorTotal(),
                OffsetDateTime.now(),
                "TRANSFERENCIA",
                null,
                KEY,
                null,
                OPERADOR_ID);
        when(recebimentoRepository.findByIdempotencyKey(KEY)).thenReturn(Optional.of(existente));

        assertThatThrownBy(() -> useCase.executar(comando(outraParcela.getId(), new BigDecimal("100.00"), KEY)))
                .isInstanceOf(ChaveIdempotenciaConflitanteException.class)
                .hasMessageContaining("parcela diferente");
    }

    @Test
    void idempotente_keyComValorDiferente_lancaConflito() {
        ParcelaCobranca parcela = novaParcela("100.00");
        Recebimento existente = parcela.registrarRecebimento(
                new BigDecimal("100.00"),
                parcela.valorTotal(),
                OffsetDateTime.now(),
                "TRANSFERENCIA",
                null,
                KEY,
                null,
                OPERADOR_ID);
        when(recebimentoRepository.findByIdempotencyKey(KEY)).thenReturn(Optional.of(existente));

        assertThatThrownBy(() -> useCase.executar(comando(parcela.getId(), new BigDecimal("40.00"), KEY)))
                .isInstanceOf(ChaveIdempotenciaConflitanteException.class)
                .hasMessageContaining("valor diferente");
    }

    @Test
    void corridaPosLock_chaveJaCriadaPorOutraThread_retornaIdempotente() {
        ParcelaCobranca parcela = novaParcela("100.00");
        // Pre-check vazio; pos-lock encontra recebimento ja criado por outra thread.
        Recebimento existente = parcela.registrarRecebimento(
                new BigDecimal("100.00"),
                parcela.valorTotal(),
                OffsetDateTime.now(),
                "TRANSFERENCIA",
                null,
                KEY,
                null,
                OPERADOR_ID);
        when(recebimentoRepository.findByIdempotencyKey(KEY))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existente));
        when(parcelaRepository.findByIdForUpdate(parcela.getId())).thenReturn(Optional.of(parcela));
        when(contratoQueryPort.propostaIdDoContrato(parcela.getAgenda().getContratoId()))
                .thenReturn(Optional.of(UUID.randomUUID()));
        when(escrowPort.registrarRecebimento(any(), any(), eq(KEY), any(), any(UUID.class)))
                .thenReturn(
                        new MovimentacaoEscrowResult(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("100.00")));

        RegistrarRecebimentoResult r = useCase.executar(comando(parcela.getId(), new BigDecimal("100.00"), KEY));

        assertThat(r.novo()).isFalse();
        verify(parcelaRepository, never()).saveAndFlush(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void escrowEhChamadoComMesmaIdempotencyKey() {
        ParcelaCobranca parcela = novaParcela("100.00");
        UUID contratoId = parcela.getAgenda().getContratoId();
        UUID propostaId = UUID.randomUUID();
        mockarFluxoNovo(parcela, contratoId, propostaId);

        useCase.executar(comando(parcela.getId(), new BigDecimal("40.00"), KEY));

        verify(escrowPort, times(1)).registrarRecebimento(eq(propostaId), any(), eq(KEY), any(), any(UUID.class));
    }

    private void mockarFluxoNovo(ParcelaCobranca parcela, UUID contratoId, UUID propostaId) {
        when(recebimentoRepository.findByIdempotencyKey(KEY)).thenReturn(Optional.empty());
        when(parcelaRepository.findByIdForUpdate(parcela.getId())).thenReturn(Optional.of(parcela));
        when(parcelaRepository.saveAndFlush(parcela)).thenReturn(parcela);
        when(contratoQueryPort.propostaIdDoContrato(contratoId)).thenReturn(Optional.of(propostaId));
        when(escrowPort.registrarRecebimento(any(), any(), any(), any(), any(UUID.class)))
                .thenReturn(
                        new MovimentacaoEscrowResult(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("100.00")));
    }

    private static RegistrarRecebimentoCommand comando(UUID parcelaId, BigDecimal valor, String key) {
        return new RegistrarRecebimentoCommand(
                parcelaId, valor, OffsetDateTime.now(), "TRANSFERENCIA", "comp-1", key, null, OPERADOR_ID);
    }

    private static ParcelaCobranca novaParcela(String valor) {
        AgendaPagamento agenda = AgendaPagamento.criar(
                UUID.randomUUID(),
                List.of(new ParcelaPlanejada(
                        1,
                        ComposicaoValor.principalApenas(new BigDecimal(valor)),
                        LocalDate.now().plusDays(30))));
        return agenda.getParcelas().get(0);
    }
}
