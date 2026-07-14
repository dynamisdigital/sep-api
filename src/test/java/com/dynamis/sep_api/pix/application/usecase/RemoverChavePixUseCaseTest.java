package com.dynamis.sep_api.pix.application.usecase;

import com.dynamis.sep_api.pix.application.port.out.ContaOperacionalEscrowQueryPort;
import com.dynamis.sep_api.pix.application.port.out.PixProvider;
import com.dynamis.sep_api.pix.application.port.out.dto.ContaOperacionalEscrowView;
import com.dynamis.sep_api.pix.application.port.out.exception.PixProviderException;
import com.dynamis.sep_api.pix.application.service.ChavePixSeguranca;
import com.dynamis.sep_api.pix.domain.event.PixChaveRemovidaEvent;
import com.dynamis.sep_api.pix.domain.model.ChavePix;
import com.dynamis.sep_api.pix.domain.vo.StatusChavePix;
import com.dynamis.sep_api.pix.domain.vo.TipoChavePix;
import com.dynamis.sep_api.pix.infrastructure.persistence.ChavePixRepository;
import com.dynamis.sep_api.shared.exception.RecursoNaoEncontradoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RemoverChavePixUseCaseTest {

    private static final String VALOR = "usuario@empresa.com";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-14T13:00:00Z"), ZoneOffset.UTC);

    private ChavePixRepository repository;
    private ContaOperacionalEscrowQueryPort contaPort;
    private PixProvider pixProvider;
    private ApplicationEventPublisher eventPublisher;
    private RemoverChavePixUseCase useCase;

    private final UUID contaEscrowId = UUID.randomUUID();
    private final UUID operadorId = UUID.randomUUID();
    private final UUID chaveId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        repository = mock(ChavePixRepository.class);
        contaPort = mock(ContaOperacionalEscrowQueryPort.class);
        pixProvider = mock(PixProvider.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        useCase = new RemoverChavePixUseCase(
                repository, contaPort, pixProvider, eventPublisher, CLOCK, mock(PlatformTransactionManager.class));

        when(contaPort.buscarContaOperacionalAtiva())
                .thenReturn(Optional.of(new ContaOperacionalEscrowView(contaEscrowId, "conta-tec-1")));
        when(repository.saveAndFlush(any(ChavePix.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private ChavePix chaveAtiva() {
        return ChavePix.cadastrar(
                contaEscrowId,
                TipoChavePix.EMAIL,
                ChavePixSeguranca.hashHex(VALOR),
                ChavePixSeguranca.mascarar(VALOR),
                "prov-key-1",
                "idem-1",
                operadorId,
                OffsetDateTime.now(CLOCK).minusDays(1));
    }

    @Test
    void chaveAtiva_chamaProviderUmaVezInativaEPublicaEvento() {
        ChavePix chave = chaveAtiva();
        when(repository.findByIdAndContaEscrowIdForUpdate(chaveId, contaEscrowId))
                .thenReturn(Optional.of(chave));

        useCase.executar(chaveId, operadorId, "corr-1");

        verify(pixProvider).removerChave("prov-key-1", "corr-1");
        assertThat(chave.getStatus()).isEqualTo(StatusChavePix.INATIVA);
        assertThat(chave.getRemovidaPorUsuarioId()).isEqualTo(operadorId);
        assertThat(chave.getRemovidaEm()).isEqualTo(OffsetDateTime.now(CLOCK));
        verify(repository).saveAndFlush(chave);

        ArgumentCaptor<PixChaveRemovidaEvent> evento = ArgumentCaptor.forClass(PixChaveRemovidaEvent.class);
        verify(eventPublisher).publishEvent(evento.capture());
        assertThat(evento.getValue().chaveId()).isEqualTo(chave.getId());
        assertThat(evento.getValue().operadorId()).isEqualTo(operadorId);
    }

    @Test
    void chaveJaInativa_sucessoIdempotenteSemProviderNemEvento() {
        ChavePix chave = chaveAtiva();
        chave.inativar(UUID.randomUUID(), OffsetDateTime.now(CLOCK).minusHours(1));
        when(repository.findByIdAndContaEscrowIdForUpdate(chaveId, contaEscrowId))
                .thenReturn(Optional.of(chave));

        assertThatCode(() -> useCase.executar(chaveId, operadorId, "corr-1")).doesNotThrowAnyException();

        verify(pixProvider, never()).removerChave(any(), any());
        verify(repository, never()).saveAndFlush(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void chaveInexistente_retorna404NeutroSemEcoarUuid() {
        when(repository.findByIdAndContaEscrowIdForUpdate(chaveId, contaEscrowId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(chaveId, operadorId, "corr-1"))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .satisfies(ex -> assertThat(ex.getMessage()).doesNotContain(chaveId.toString()));
        verify(pixProvider, never()).removerChave(any(), any());
    }

    @Test
    void contaOperacionalAusente_retorna404NeutroSemConsultarChave() {
        when(contaPort.buscarContaOperacionalAtiva()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(chaveId, operadorId, "corr-1"))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .satisfies(ex -> assertThat(ex.getMessage()).doesNotContain(chaveId.toString()));
        verify(repository, never()).findByIdAndContaEscrowIdForUpdate(any(), any());
    }

    @Test
    void providerFalha_chavePermaneceAtivaSemAuditoria() {
        ChavePix chave = chaveAtiva();
        when(repository.findByIdAndContaEscrowIdForUpdate(chaveId, contaEscrowId))
                .thenReturn(Optional.of(chave));
        doThrow(new PixProviderException("falha tecnica")).when(pixProvider).removerChave(any(), any());

        assertThatThrownBy(() -> useCase.executar(chaveId, operadorId, "corr-1"))
                .isInstanceOf(PixProviderException.class);

        assertThat(chave.getStatus()).isEqualTo(StatusChavePix.ATIVA);
        verify(repository, never()).saveAndFlush(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void erroDeRemocao_naoContemValorHashMascaraNemProviderId() {
        when(repository.findByIdAndContaEscrowIdForUpdate(chaveId, contaEscrowId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(chaveId, operadorId, "corr-1"))
                .satisfies(ex -> assertThat(ex.getMessage())
                        .doesNotContain(VALOR)
                        .doesNotContain(ChavePixSeguranca.hashHex(VALOR))
                        .doesNotContain("prov-key-1"));
    }
}
