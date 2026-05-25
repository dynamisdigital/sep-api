package com.dynamis.sep_api.cobranca.application.job;

import com.dynamis.sep_api.cobranca.application.dto.EscalarCobrancaCommand;
import com.dynamis.sep_api.cobranca.application.dto.EscalonamentoResult;
import com.dynamis.sep_api.cobranca.application.port.out.ContratoCobrancaQueryPort;
import com.dynamis.sep_api.cobranca.application.usecase.EscalarCobrancaUseCase;
import com.dynamis.sep_api.cobranca.domain.event.ParcelaInadimplenteEvent;
import com.dynamis.sep_api.cobranca.domain.model.AgendaPagamento;
import com.dynamis.sep_api.cobranca.domain.model.AgendaPagamento.ParcelaPlanejada;
import com.dynamis.sep_api.cobranca.domain.model.ParcelaCobranca;
import com.dynamis.sep_api.cobranca.domain.vo.ComposicaoValor;
import com.dynamis.sep_api.cobranca.domain.vo.StatusParcela;
import com.dynamis.sep_api.cobranca.infrastructure.persistence.ParcelaCobrancaRepository;
import com.dynamis.sep_api.usuarios.domain.model.Role;
import com.dynamis.sep_api.usuarios.domain.model.Usuario;
import com.dynamis.sep_api.usuarios.infrastructure.persistence.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarcarParcelaInadimplenteJobTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-20T10:00:00Z"), ZoneOffset.UTC);
    // hoje (2026-06-20) - 90 dias = 2026-03-22; parcela vencida em 2026-03-15 tem 97 dias.
    private static final LocalDate VENCIMENTO_97_DIAS = LocalDate.of(2026, 3, 15);
    private static final LocalDate VENCIMENTO_89_DIAS = LocalDate.of(2026, 3, 23);

    private ParcelaCobrancaRepository parcelaRepository;
    private ContratoCobrancaQueryPort contratoQuery;
    private UsuarioRepository usuarioRepository;
    private EscalarCobrancaUseCase escalarUseCase;
    private ApplicationEventPublisher eventPublisher;
    private TransactionTemplate txTemplate;
    private MarcarParcelaInadimplenteJob job;

    @BeforeEach
    void setup() {
        parcelaRepository = mock(ParcelaCobrancaRepository.class);
        contratoQuery = mock(ContratoCobrancaQueryPort.class);
        usuarioRepository = mock(UsuarioRepository.class);
        escalarUseCase = mock(EscalarCobrancaUseCase.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        txTemplate = mock(TransactionTemplate.class);
        // TransactionTemplate.executeWithoutResult delega para o Consumer; aqui simulamos
        // execucao sincrona invocando a lambda recebida.
        doAnswer(invocation -> {
                    java.util.function.Consumer<org.springframework.transaction.TransactionStatus> action =
                            invocation.getArgument(0);
                    action.accept(null);
                    return null;
                })
                .when(txTemplate)
                .executeWithoutResult(any());
        when(txTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<Object> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        when(escalarUseCase.escalar(any())).thenReturn(new EscalonamentoResult(true, false, false, true, 0));
        job = new MarcarParcelaInadimplenteJob(
                parcelaRepository, contratoQuery, usuarioRepository, escalarUseCase, eventPublisher, txTemplate, CLOCK);
    }

    @Test
    void executar_transicionaAtrasada90Dias_paraInadimplenteEPublicaEvento() {
        ParcelaCobranca parcela = parcelaCom(VENCIMENTO_97_DIAS);
        UUID parcelaId = parcela.getId();
        when(parcelaRepository.findByStatusAndDataVencimentoBefore(any(), any()))
                .thenReturn(List.of(parcela));
        when(parcelaRepository.findById(parcelaId)).thenReturn(Optional.of(parcela));
        when(parcelaRepository.findByIdForUpdate(parcelaId)).thenReturn(Optional.of(parcela));
        UUID tomadorId = UUID.randomUUID();
        when(contratoQuery.tomadorIdDoContrato(any())).thenReturn(Optional.of(tomadorId));
        when(usuarioRepository.findById(tomadorId))
                .thenReturn(Optional.of(Usuario.criar("x@y.com", "h", Role.CLIENTE)));

        int processadas = job.executar();

        assertThat(processadas).isEqualTo(1);
        assertThat(parcela.getStatus()).isEqualTo(StatusParcela.INADIMPLENTE);
        verify(parcelaRepository).save(parcela);
        ArgumentCaptor<ParcelaInadimplenteEvent> captor = ArgumentCaptor.forClass(ParcelaInadimplenteEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().parcelaId()).isEqualTo(parcelaId);
        assertThat(captor.getValue().diasAtraso()).isEqualTo(97);
        assertThat(captor.getValue().tomadorId()).isEqualTo(tomadorId);
    }

    @Test
    void executar_filtraDiasMenorQue90() {
        ParcelaCobranca parcela = parcelaCom(VENCIMENTO_89_DIAS);
        when(parcelaRepository.findByStatusAndDataVencimentoBefore(any(), any()))
                .thenReturn(List.of(parcela));

        int processadas = job.executar();

        assertThat(processadas).isZero();
        verify(parcelaRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void executar_notificacaoFalha_naoBloqueiaTransicao() {
        ParcelaCobranca parcela = parcelaCom(VENCIMENTO_97_DIAS);
        UUID parcelaId = parcela.getId();
        when(parcelaRepository.findByStatusAndDataVencimentoBefore(any(), any()))
                .thenReturn(List.of(parcela));
        when(parcelaRepository.findById(parcelaId)).thenReturn(Optional.of(parcela));
        when(parcelaRepository.findByIdForUpdate(parcelaId)).thenReturn(Optional.of(parcela));
        when(contratoQuery.tomadorIdDoContrato(any())).thenReturn(Optional.of(UUID.randomUUID()));
        when(escalarUseCase.escalar(any())).thenThrow(new RuntimeException("smtp down"));

        int processadas = job.executar();

        assertThat(processadas).isEqualTo(1);
        assertThat(parcela.getStatus()).isEqualTo(StatusParcela.INADIMPLENTE);
        verify(eventPublisher).publishEvent(any(ParcelaInadimplenteEvent.class));
    }

    @Test
    void executar_idempotente_parcelaJaInadimplenteNaoRepublica() {
        ParcelaCobranca parcela = parcelaCom(VENCIMENTO_97_DIAS);
        parcela.marcarInadimplente(); // ja transicionada por execucao previa
        when(parcelaRepository.findByStatusAndDataVencimentoBefore(any(), any()))
                .thenReturn(List.of());

        int processadas = job.executar();

        assertThat(processadas).isZero();
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void executar_concorrencia_statusMudouEntreLeituraELock_pula() {
        ParcelaCobranca parcela = parcelaCom(VENCIMENTO_97_DIAS);
        UUID parcelaId = parcela.getId();
        when(parcelaRepository.findByStatusAndDataVencimentoBefore(any(), any()))
                .thenReturn(List.of(parcela));
        when(parcelaRepository.findById(parcelaId)).thenReturn(Optional.of(parcela));
        // Outra instancia marcou primeiro — findByIdForUpdate retorna parcela ja INADIMPLENTE.
        ParcelaCobranca clone = parcelaCom(VENCIMENTO_97_DIAS);
        clone.marcarInadimplente();
        when(parcelaRepository.findByIdForUpdate(parcelaId)).thenReturn(Optional.of(clone));

        int processadas = job.executar();

        assertThat(processadas).isEqualTo(1);
        verify(parcelaRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void executar_invokaUseCaseComDiasAtrasoReal() {
        ParcelaCobranca parcela = parcelaCom(VENCIMENTO_97_DIAS);
        UUID parcelaId = parcela.getId();
        when(parcelaRepository.findByStatusAndDataVencimentoBefore(any(), any()))
                .thenReturn(List.of(parcela));
        when(parcelaRepository.findById(parcelaId)).thenReturn(Optional.of(parcela));
        when(parcelaRepository.findByIdForUpdate(parcelaId)).thenReturn(Optional.of(parcela));
        when(contratoQuery.tomadorIdDoContrato(any())).thenReturn(Optional.of(UUID.randomUUID()));
        when(usuarioRepository.findById(any())).thenReturn(Optional.of(Usuario.criar("x@y.com", "h", Role.CLIENTE)));

        job.executar();

        ArgumentCaptor<EscalarCobrancaCommand> captor = ArgumentCaptor.forClass(EscalarCobrancaCommand.class);
        verify(escalarUseCase, times(1)).escalar(captor.capture());
        assertThat(captor.getValue().diasAtraso()).isEqualTo(97);
        assertThat(captor.getValue().emailTomador()).isEqualTo("x@y.com");
    }

    @Test
    void executar_falhaTransicao_naoQuebraLoop() {
        ParcelaCobranca p1 = parcelaCom(VENCIMENTO_97_DIAS);
        ParcelaCobranca p2 = parcelaCom(VENCIMENTO_97_DIAS);
        when(parcelaRepository.findByStatusAndDataVencimentoBefore(any(), any()))
                .thenReturn(List.of(p1, p2));
        when(parcelaRepository.findById(p1.getId())).thenReturn(Optional.of(p1));
        when(parcelaRepository.findById(p2.getId())).thenReturn(Optional.of(p2));
        when(parcelaRepository.findByIdForUpdate(p1.getId())).thenThrow(new RuntimeException("lock timeout"));
        when(parcelaRepository.findByIdForUpdate(p2.getId())).thenReturn(Optional.of(p2));
        when(contratoQuery.tomadorIdDoContrato(any())).thenReturn(Optional.of(UUID.randomUUID()));
        when(usuarioRepository.findById(any())).thenReturn(Optional.of(Usuario.criar("x@y.com", "h", Role.CLIENTE)));

        int processadas = job.executar();

        assertThat(processadas).isEqualTo(1);
        assertThat(p2.getStatus()).isEqualTo(StatusParcela.INADIMPLENTE);
    }

    private static ParcelaCobranca parcelaCom(LocalDate dataVencimento) {
        AgendaPagamento agenda = AgendaPagamento.criar(
                UUID.randomUUID(),
                List.of(new ParcelaPlanejada(
                        1, ComposicaoValor.principalApenas(new BigDecimal("100.00")), dataVencimento)));
        ParcelaCobranca parcela = agenda.getParcelas().get(0);
        parcela.marcarAtrasada();
        return parcela;
    }
}
