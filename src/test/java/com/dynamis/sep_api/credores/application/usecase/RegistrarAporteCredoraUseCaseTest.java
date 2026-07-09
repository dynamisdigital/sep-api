package com.dynamis.sep_api.credores.application.usecase;

import com.dynamis.sep_api.credores.application.dto.RegistrarAporteCredoraCommand;
import com.dynamis.sep_api.credores.application.dto.RegistrarAporteCredoraResult;
import com.dynamis.sep_api.credores.application.port.out.AporteEscrowRegistrado;
import com.dynamis.sep_api.credores.application.port.out.ConsultarContratoParaCarteiraCredoraPort;
import com.dynamis.sep_api.credores.application.port.out.ContratoCarteiraView;
import com.dynamis.sep_api.credores.application.port.out.RegistrarAporteEscrowCommand;
import com.dynamis.sep_api.credores.application.port.out.RegistrarAporteEscrowPort;
import com.dynamis.sep_api.credores.domain.event.AporteCredoraRegistradoEvent;
import com.dynamis.sep_api.credores.domain.exception.AporteConflitanteException;
import com.dynamis.sep_api.credores.domain.exception.AporteEscrowException;
import com.dynamis.sep_api.credores.domain.exception.AporteNaoElegivelException;
import com.dynamis.sep_api.credores.domain.exception.AporteOperacaoNaoEncontradaException;
import com.dynamis.sep_api.credores.domain.model.AporteCredora;
import com.dynamis.sep_api.credores.domain.model.OperacaoFinanciada;
import com.dynamis.sep_api.credores.domain.vo.StatusAporteCredora;
import com.dynamis.sep_api.credores.infrastructure.persistence.AporteCredoraRepository;
import com.dynamis.sep_api.credores.infrastructure.persistence.OperacaoFinanciadaRepository;
import com.dynamis.sep_api.shared.exception.ValidacaoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testes do registro assistido de aporte (Sprint 29 Task 29.3): elegibilidade carteira + contrato
 * ASSINADO, 404 neutro, idempotencia por operacao + chave, erros controlados e auditoria unica.
 */
class RegistrarAporteCredoraUseCaseTest {

    private static final String KEY = "op-key-1";
    private static final UUID ATOR = UUID.randomUUID();

    private OperacaoFinanciadaRepository operacaoRepository;
    private AporteCredoraRepository aporteRepository;
    private ConsultarContratoParaCarteiraCredoraPort contratoPort;
    private RegistrarAporteEscrowPort aporteEscrowPort;
    private ApplicationEventPublisher eventPublisher;
    private RegistrarAporteCredoraUseCase useCase;

    private OperacaoFinanciada operacao;
    private UUID propostaId;

    @BeforeEach
    void setup() {
        operacaoRepository = mock(OperacaoFinanciadaRepository.class);
        aporteRepository = mock(AporteCredoraRepository.class);
        contratoPort = mock(ConsultarContratoParaCarteiraCredoraPort.class);
        aporteEscrowPort = mock(RegistrarAporteEscrowPort.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        useCase = new RegistrarAporteCredoraUseCase(
                operacaoRepository, aporteRepository, contratoPort, aporteEscrowPort, eventPublisher);

        operacao = OperacaoFinanciada.associar(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Associacao assistida");
        propostaId = UUID.randomUUID();
        when(operacaoRepository.findByIdForUpdate(operacao.getId())).thenReturn(Optional.of(operacao));
        when(aporteRepository.findByOperacaoIdAndIdempotencyKey(operacao.getId(), KEY))
                .thenReturn(Optional.empty());
        when(contratoPort.consultarPorId(operacao.getContratoId())).thenReturn(Optional.of(contratoView("ASSINADO")));
        when(aporteRepository.save(any(AporteCredora.class))).thenAnswer(i -> i.getArgument(0));
        when(aporteEscrowPort.registrar(any()))
                .thenReturn(new AporteEscrowRegistrado("mov-escrow-1", "EM_PROCESSAMENTO"));
    }

    @Test
    void registraAporteElegivelComContratoAssinado() {
        RegistrarAporteCredoraResult resultado = useCase.executar(comando("2500.00"));

        assertThat(resultado.novo()).isTrue();
        assertThat(resultado.aporte().operacaoId()).isEqualTo(operacao.getId());
        assertThat(resultado.aporte().status()).isEqualTo(StatusAporteCredora.EM_PROCESSAMENTO);
        assertThat(resultado.aporte().valor()).isEqualByComparingTo("2500.00");

        ArgumentCaptor<RegistrarAporteEscrowCommand> escrowCaptor =
                ArgumentCaptor.forClass(RegistrarAporteEscrowCommand.class);
        verify(aporteEscrowPort).registrar(escrowCaptor.capture());
        assertThat(escrowCaptor.getValue().aporteId())
                .isEqualTo(resultado.aporte().id());
        assertThat(escrowCaptor.getValue().propostaId()).isEqualTo(propostaId);
        assertThat(escrowCaptor.getValue().valor()).isEqualByComparingTo("2500.00");

        ArgumentCaptor<AporteCredoraRegistradoEvent> eventoCaptor =
                ArgumentCaptor.forClass(AporteCredoraRegistradoEvent.class);
        verify(eventPublisher).publishEvent(eventoCaptor.capture());
        AporteCredoraRegistradoEvent evento = eventoCaptor.getValue();
        assertThat(evento.aporteId()).isEqualTo(resultado.aporte().id());
        assertThat(evento.operacaoId()).isEqualTo(operacao.getId());
        assertThat(evento.empresaCredoraId()).isEqualTo(operacao.getEmpresaCredoraId());
        assertThat(evento.usuarioId()).isEqualTo(ATOR);
    }

    @Test
    void operacaoInexistenteRetornaErroNeutroSemConsultarNada() {
        UUID inexistente = UUID.randomUUID();
        when(operacaoRepository.findByIdForUpdate(inexistente)).thenReturn(Optional.empty());

        Throwable erro = catchThrowable(() ->
                useCase.executar(new RegistrarAporteCredoraCommand(inexistente, new BigDecimal("100.00"), KEY, ATOR)));

        assertThat(erro)
                .isInstanceOf(AporteOperacaoNaoEncontradaException.class)
                .hasMessageNotContaining(inexistente.toString());
        verify(contratoPort, never()).consultarPorId(any());
        verify(aporteEscrowPort, never()).registrar(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void operacaoEncerradaNaoRegistraAporte() {
        operacao.encerrar();

        assertThatThrownBy(() -> useCase.executar(comando("100.00"))).isInstanceOf(AporteNaoElegivelException.class);
        verify(aporteRepository, never()).save(any());
        verify(aporteEscrowPort, never()).registrar(any());
    }

    @Test
    void contratoNaoAssinadoRetorna409() {
        when(contratoPort.consultarPorId(operacao.getContratoId())).thenReturn(Optional.of(contratoView("GERADO")));

        assertThatThrownBy(() -> useCase.executar(comando("100.00"))).isInstanceOf(AporteNaoElegivelException.class);
        verify(aporteEscrowPort, never()).registrar(any());
    }

    @Test
    void contratoAusenteNaPortaRetorna409() {
        when(contratoPort.consultarPorId(operacao.getContratoId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(comando("100.00"))).isInstanceOf(AporteNaoElegivelException.class);
        verify(aporteEscrowPort, never()).registrar(any());
    }

    @Test
    void replayComMesmaChaveEMesmoValorRetornaAporteExistenteSemNovoRegistro() {
        AporteCredora existente = AporteCredora.registrar(
                operacao.getId(), operacao.getEmpresaCredoraId(), new BigDecimal("2500.00"), KEY);
        existente.marcarEmProcessamento("mov-escrow-1");
        when(aporteRepository.findByOperacaoIdAndIdempotencyKey(operacao.getId(), KEY))
                .thenReturn(Optional.of(existente));

        RegistrarAporteCredoraResult resultado = useCase.executar(comando("2500.00"));

        assertThat(resultado.novo()).isFalse();
        assertThat(resultado.aporte().id()).isEqualTo(existente.getId());
        verify(aporteRepository, never()).save(any());
        verify(aporteEscrowPort, never()).registrar(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void replayEstavelMesmoComOperacaoEncerradaDepoisDoRegistro() {
        AporteCredora existente = AporteCredora.registrar(
                operacao.getId(), operacao.getEmpresaCredoraId(), new BigDecimal("2500.00"), KEY);
        when(aporteRepository.findByOperacaoIdAndIdempotencyKey(operacao.getId(), KEY))
                .thenReturn(Optional.of(existente));
        operacao.encerrar();

        RegistrarAporteCredoraResult resultado = useCase.executar(comando("2500.00"));

        assertThat(resultado.novo()).isFalse();
        assertThat(resultado.aporte().id()).isEqualTo(existente.getId());
    }

    @Test
    void mesmaChaveComValorDivergenteFalhaControlado() {
        AporteCredora existente = AporteCredora.registrar(
                operacao.getId(), operacao.getEmpresaCredoraId(), new BigDecimal("2500.00"), KEY);
        when(aporteRepository.findByOperacaoIdAndIdempotencyKey(operacao.getId(), KEY))
                .thenReturn(Optional.of(existente));

        Throwable erro = catchThrowable(() -> useCase.executar(comando("999.99")));

        assertThat(erro)
                .isInstanceOf(AporteConflitanteException.class)
                .hasMessageNotContaining(KEY)
                .hasMessageNotContaining("999.99");
        verify(aporteEscrowPort, never()).registrar(any());
    }

    @Test
    void falhaDoEscrowPropagaSemEventoDeAuditoria() {
        AporteEscrowException falha = new AporteEscrowException(new RuntimeException("erro bruto"));
        when(aporteEscrowPort.registrar(any())).thenThrow(falha);

        assertThatThrownBy(() -> useCase.executar(comando("100.00"))).isSameAs(falha);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void validaChaveEValor() {
        assertThatThrownBy(() -> useCase.executar(
                        new RegistrarAporteCredoraCommand(operacao.getId(), new BigDecimal("100.00"), " ", ATOR)))
                .isInstanceOf(ValidacaoException.class);
        assertThatThrownBy(() -> useCase.executar(new RegistrarAporteCredoraCommand(
                        operacao.getId(), new BigDecimal("100.00"), "k".repeat(101), ATOR)))
                .isInstanceOf(ValidacaoException.class);
        assertThatThrownBy(() -> useCase.executar(
                        new RegistrarAporteCredoraCommand(operacao.getId(), BigDecimal.ZERO, KEY, ATOR)))
                .isInstanceOf(ValidacaoException.class);
        assertThatThrownBy(() -> useCase.executar(
                        new RegistrarAporteCredoraCommand(operacao.getId(), new BigDecimal("10.123"), KEY, ATOR)))
                .isInstanceOf(ValidacaoException.class);
        assertThatThrownBy(() ->
                        useCase.executar(new RegistrarAporteCredoraCommand(null, new BigDecimal("100.00"), KEY, ATOR)))
                .isInstanceOf(ValidacaoException.class);
        verify(operacaoRepository, never()).findByIdForUpdate(any());
    }

    private RegistrarAporteCredoraCommand comando(String valor) {
        return new RegistrarAporteCredoraCommand(operacao.getId(), new BigDecimal(valor), KEY, ATOR);
    }

    private ContratoCarteiraView contratoView(String status) {
        return new ContratoCarteiraView(operacao.getContratoId(), propostaId, status);
    }
}
