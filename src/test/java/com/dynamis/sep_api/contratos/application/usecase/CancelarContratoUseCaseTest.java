package com.dynamis.sep_api.contratos.application.usecase;

import com.dynamis.sep_api.contratos.application.usecase.command.CancelarContratoCommand;
import com.dynamis.sep_api.contratos.domain.event.ContratoCanceladoEvent;
import com.dynamis.sep_api.contratos.domain.exception.ContratoEstadoInvalidoException;
import com.dynamis.sep_api.contratos.domain.exception.ContratoNaoEncontradoException;
import com.dynamis.sep_api.contratos.domain.model.Contrato;
import com.dynamis.sep_api.contratos.domain.vo.StatusFormalizacao;
import com.dynamis.sep_api.contratos.domain.vo.TipoContrato;
import com.dynamis.sep_api.contratos.infrastructure.persistence.ContratoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CancelarContratoUseCaseTest {

    private static final String HASH = "2".repeat(64);
    private static final String JUSTIFICATIVA = "Cancelado por divergencia operacional antes do aceite.";

    @Mock
    private ContratoRepository contratoRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private CancelarContratoUseCase useCase;

    private Contrato contrato;
    private UUID canceladoPorId;

    @BeforeEach
    void setup() {
        contrato = Contrato.criar(UUID.randomUUID(), UUID.randomUUID(), TipoContrato.MUTUO);
        contrato.adicionarVersao("v1", HASH);
        canceladoPorId = UUID.randomUUID();
    }

    @Test
    void executar_sucessoTransicionaParaCanceladoEPublicaEvento() {
        when(contratoRepository.findByIdForUpdate(contrato.getId())).thenReturn(Optional.of(contrato));
        when(contratoRepository.save(any(Contrato.class))).thenAnswer(inv -> inv.getArgument(0));

        Contrato resultado =
                useCase.executar(new CancelarContratoCommand(contrato.getId(), canceladoPorId, JUSTIFICATIVA));

        assertThat(resultado.getStatus()).isEqualTo(StatusFormalizacao.CANCELADO);
        verify(contratoRepository, times(1)).save(contrato);
        verify(eventPublisher, times(1))
                .publishEvent(argThat((Object e) -> e instanceof ContratoCanceladoEvent evento
                        && evento.contratoId().equals(contrato.getId())
                        && evento.canceladoPorId().equals(canceladoPorId)
                        && evento.justificativa().equals(JUSTIFICATIVA)));
    }

    @Test
    void executar_emGerado_cancelaOk() {
        Contrato semVersao = Contrato.criar(UUID.randomUUID(), UUID.randomUUID(), TipoContrato.MUTUO);
        // Estado GERADO (sem versao)
        when(contratoRepository.findByIdForUpdate(semVersao.getId())).thenReturn(Optional.of(semVersao));
        when(contratoRepository.save(any(Contrato.class))).thenAnswer(inv -> inv.getArgument(0));

        Contrato resultado =
                useCase.executar(new CancelarContratoCommand(semVersao.getId(), canceladoPorId, JUSTIFICATIVA));

        assertThat(resultado.getStatus()).isEqualTo(StatusFormalizacao.CANCELADO);
    }

    @Test
    void executar_contratoInexistente_lanca404() {
        UUID id = UUID.randomUUID();
        when(contratoRepository.findByIdForUpdate(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(new CancelarContratoCommand(id, canceladoPorId, JUSTIFICATIVA)))
                .isInstanceOf(ContratoNaoEncontradoException.class);
    }

    @Test
    void executar_contratoJaAceito_lanca409() {
        contrato.marcarAceito();
        when(contratoRepository.findByIdForUpdate(contrato.getId())).thenReturn(Optional.of(contrato));

        assertThatThrownBy(() ->
                        useCase.executar(new CancelarContratoCommand(contrato.getId(), canceladoPorId, JUSTIFICATIVA)))
                .isInstanceOf(ContratoEstadoInvalidoException.class);
        verify(contratoRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void executar_contratoJaCancelado_lanca409() {
        contrato.cancelar();
        when(contratoRepository.findByIdForUpdate(contrato.getId())).thenReturn(Optional.of(contrato));

        assertThatThrownBy(() ->
                        useCase.executar(new CancelarContratoCommand(contrato.getId(), canceladoPorId, JUSTIFICATIVA)))
                .isInstanceOf(ContratoEstadoInvalidoException.class);
    }

    @Test
    void command_recusaJustificativaCurta() {
        assertThatThrownBy(() -> new CancelarContratoCommand(UUID.randomUUID(), UUID.randomUUID(), "curta"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("justificativa");
    }

    @Test
    void command_recusaJustificativaLonga() {
        String longa = "x".repeat(501);
        assertThatThrownBy(() -> new CancelarContratoCommand(UUID.randomUUID(), UUID.randomUUID(), longa))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("justificativa");
    }

    @Test
    void command_aplicaTrim() {
        CancelarContratoCommand cmd =
                new CancelarContratoCommand(UUID.randomUUID(), UUID.randomUUID(), "   " + JUSTIFICATIVA + "   ");

        assertThat(cmd.justificativa()).isEqualTo(JUSTIFICATIVA);
    }

    @Test
    void command_recusaNull() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> new CancelarContratoCommand(null, id, JUSTIFICATIVA))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CancelarContratoCommand(id, null, JUSTIFICATIVA))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CancelarContratoCommand(id, id, null)).isInstanceOf(NullPointerException.class);
    }
}
