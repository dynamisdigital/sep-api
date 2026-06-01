package com.dynamis.sep_api.pix.application.usecase;

import com.dynamis.sep_api.pix.application.dto.SolicitarDesembolsoPixCommand;
import com.dynamis.sep_api.pix.application.dto.SolicitarDesembolsoPixResult;
import com.dynamis.sep_api.pix.application.port.out.PixProvider;
import com.dynamis.sep_api.pix.application.port.out.dto.AgendaDesembolsoView;
import com.dynamis.sep_api.pix.application.port.out.dto.ContratoDesembolsoView;
import com.dynamis.sep_api.pix.application.port.out.dto.EscrowDesembolsoView;
import com.dynamis.sep_api.pix.application.port.out.dto.RespostaTransferenciaPix;
import com.dynamis.sep_api.pix.application.port.out.dto.StatusTransferenciaPixProvider;
import com.dynamis.sep_api.pix.application.port.out.exception.PixProviderException;
import com.dynamis.sep_api.pix.application.service.ChavePixSeguranca;
import com.dynamis.sep_api.pix.application.service.DesembolsoTransacaoService;
import com.dynamis.sep_api.pix.application.service.ResultadoElegibilidadeDesembolso;
import com.dynamis.sep_api.pix.application.service.ResultadoElegibilidadeDesembolso.MotivoInelegibilidade;
import com.dynamis.sep_api.pix.application.service.SincronizadorStatusTransferencia;
import com.dynamis.sep_api.pix.application.service.ValidadorElegibilidadeDesembolso;
import com.dynamis.sep_api.pix.domain.model.PixTransferencia;
import com.dynamis.sep_api.pix.domain.vo.StatusPixTransferencia;
import com.dynamis.sep_api.pix.infrastructure.persistence.PixTransferenciaRepository;
import com.dynamis.sep_api.shared.exception.ConflitoException;
import com.dynamis.sep_api.shared.exception.OperacaoNaoProcessavelException;
import com.dynamis.sep_api.shared.exception.RecursoNaoEncontradoException;
import com.dynamis.sep_api.shared.exception.ValidacaoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SolicitarDesembolsoPixUseCaseTest {

    private PixTransferenciaRepository repository;
    private ValidadorElegibilidadeDesembolso validador;
    private PixProvider pixProvider;
    private SolicitarDesembolsoPixUseCase useCase;

    private final UUID contratoId = UUID.randomUUID();
    private final UUID propostaId = UUID.randomUUID();
    private final UUID tomadorId = UUID.randomUUID();
    private final UUID operadorId = UUID.randomUUID();
    private static final BigDecimal VALOR = new BigDecimal("10000.00");
    private static final String CHAVE = "usuario@empresa.com";

    @BeforeEach
    void setUp() {
        repository = mock(PixTransferenciaRepository.class);
        validador = mock(ValidadorElegibilidadeDesembolso.class);
        pixProvider = mock(PixProvider.class);
        SincronizadorStatusTransferencia sincronizador =
                new SincronizadorStatusTransferencia(mock(ApplicationEventPublisher.class));
        DesembolsoTransacaoService transacao = new DesembolsoTransacaoService(repository, sincronizador);
        useCase = new SolicitarDesembolsoPixUseCase(repository, validador, pixProvider, transacao);
    }

    private SolicitarDesembolsoPixCommand comando(BigDecimal valor, String idempotencyKey) {
        return new SolicitarDesembolsoPixCommand(contratoId, valor, CHAVE, idempotencyKey, operadorId, "corr-1");
    }

    private void stubElegivel() {
        ContratoDesembolsoView contrato = new ContratoDesembolsoView(contratoId, propostaId, tomadorId, VALOR, true);
        AgendaDesembolsoView agenda = new AgendaDesembolsoView(contratoId, UUID.randomUUID(), 12);
        EscrowDesembolsoView escrow = new EscrowDesembolsoView(propostaId, UUID.randomUUID(), "ext", true);
        when(validador.validar(contratoId))
                .thenReturn(ResultadoElegibilidadeDesembolso.elegivel(contrato, agenda, escrow));
    }

    private void stubInelegivel(MotivoInelegibilidade motivo) {
        when(validador.validar(contratoId)).thenReturn(ResultadoElegibilidadeDesembolso.inelegivel(motivo));
    }

    /** Captura a transferencia inserida (fase 1) para servir as fases seguintes (findById/save). */
    private final java.util.concurrent.atomic.AtomicReference<PixTransferencia> inserida =
            new java.util.concurrent.atomic.AtomicReference<>();

    /** Stuba o caminho feliz ate a chamada ao provider (sem definir a resposta do provider). */
    private void stubCaminhoAteProvider(String idempotencyKey) {
        when(repository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        stubElegivel();
        when(repository.findFirstByContratoIdAndStatusInOrderByDataCriacaoDesc(eq(contratoId), anyCollection()))
                .thenReturn(Optional.empty());
        // Fase 1 (inserirCriada): saveAndFlush captura a entidade e devolve com id.
        when(repository.saveAndFlush(any())).thenAnswer(inv -> {
            PixTransferencia t = inv.getArgument(0);
            inserida.set(t);
            return t;
        });
        // Fase 2 (aplicarResposta/marcarFalha): recarrega por id + salva.
        when(repository.findById(any())).thenAnswer(inv -> Optional.ofNullable(inserida.get()));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private void stubRespostaProvider(StatusTransferenciaPixProvider status) {
        when(pixProvider.solicitarTransferencia(any(), any(), any()))
                .thenReturn(new RespostaTransferenciaPix("ext-pix-1", status));
    }

    @Test
    void providerPendente_transferenciaFicaSolicitada() {
        stubCaminhoAteProvider("idem-1");
        stubRespostaProvider(StatusTransferenciaPixProvider.PENDENTE);

        SolicitarDesembolsoPixResult res = useCase.executar(comando(VALOR, "idem-1"));

        assertThat(res.novo()).isTrue();
        assertThat(res.status()).isEqualTo(StatusPixTransferencia.SOLICITADA);
        assertThat(res.contratoId()).isEqualTo(contratoId);
        assertThat(res.chaveDestinoMascara()).contains("*").doesNotContain("empresa");
    }

    @Test
    void providerProcessando_transferenciaFicaProcessando() {
        stubCaminhoAteProvider("idem-1");
        stubRespostaProvider(StatusTransferenciaPixProvider.PROCESSANDO);

        SolicitarDesembolsoPixResult res = useCase.executar(comando(VALOR, "idem-1"));

        assertThat(res.status()).isEqualTo(StatusPixTransferencia.PROCESSANDO);
    }

    @Test
    void providerConcluida_transferenciaFicaConcluida() {
        stubCaminhoAteProvider("idem-1");
        stubRespostaProvider(StatusTransferenciaPixProvider.CONCLUIDA);

        SolicitarDesembolsoPixResult res = useCase.executar(comando(VALOR, "idem-1"));

        assertThat(res.status()).isEqualTo(StatusPixTransferencia.CONCLUIDA);
    }

    @Test
    void providerRejeitada_transferenciaFicaFalhou() {
        stubCaminhoAteProvider("idem-1");
        stubRespostaProvider(StatusTransferenciaPixProvider.REJEITADA);

        SolicitarDesembolsoPixResult res = useCase.executar(comando(VALOR, "idem-1"));

        assertThat(res.status()).isEqualTo(StatusPixTransferencia.FALHOU);
    }

    @Test
    void providerLancaExcecao_transferenciaFicaFalhouRastreavel() {
        stubCaminhoAteProvider("idem-1");
        when(pixProvider.solicitarTransferencia(any(), any(), any())).thenThrow(new PixProviderException("timeout"));

        SolicitarDesembolsoPixResult res = useCase.executar(comando(VALOR, "idem-1"));

        assertThat(res.status()).isEqualTo(StatusPixTransferencia.FALHOU);
        assertThat(res.novo()).isTrue();
    }

    @Test
    void replayMesmaKeyMesmoPayload_retornaExistenteSemSalvar() {
        PixTransferencia existente = PixTransferencia.criarDesembolso(
                contratoId,
                propostaId,
                tomadorId,
                VALOR,
                ChavePixSeguranca.hashHex(CHAVE),
                ChavePixSeguranca.mascarar(CHAVE),
                "idem-1",
                "corr-1");
        when(repository.findByIdempotencyKey("idem-1")).thenReturn(Optional.of(existente));

        SolicitarDesembolsoPixResult res = useCase.executar(comando(VALOR, "idem-1"));

        assertThat(res.novo()).isFalse();
        assertThat(res.transferenciaId()).isEqualTo(existente.getId());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void replayMesmaKeyPayloadDivergente_conflito() {
        PixTransferencia existente = PixTransferencia.criarDesembolso(
                contratoId,
                propostaId,
                tomadorId,
                VALOR,
                ChavePixSeguranca.hashHex(CHAVE),
                ChavePixSeguranca.mascarar(CHAVE),
                "idem-1",
                "corr-1");
        when(repository.findByIdempotencyKey("idem-1")).thenReturn(Optional.of(existente));

        assertThatThrownBy(() -> useCase.executar(comando(new BigDecimal("9999.00"), "idem-1")))
                .isInstanceOf(ConflitoException.class)
                .extracting("codigo")
                .isEqualTo("PIX-409-IDEMPOTENCIA");
    }

    @Test
    void idempotencyKeyAcimaDe100Chars_validacao400() {
        String longa = "k".repeat(101);

        assertThatThrownBy(() -> useCase.executar(comando(VALOR, longa)))
                .isInstanceOf(ValidacaoException.class)
                .extracting("codigo")
                .isEqualTo("PIX-400-IDEMPOTENCY-KEY-TAMANHO");
    }

    @Test
    void contratoInexistente_naoEncontrado() {
        when(repository.findByIdempotencyKey("idem-1")).thenReturn(Optional.empty());
        stubInelegivel(MotivoInelegibilidade.CONTRATO_NAO_ENCONTRADO);

        assertThatThrownBy(() -> useCase.executar(comando(VALOR, "idem-1")))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void contratoNaoAssinado_naoProcessavel() {
        when(repository.findByIdempotencyKey("idem-1")).thenReturn(Optional.empty());
        stubInelegivel(MotivoInelegibilidade.CONTRATO_NAO_ASSINADO);

        assertThatThrownBy(() -> useCase.executar(comando(VALOR, "idem-1")))
                .isInstanceOf(OperacaoNaoProcessavelException.class)
                .extracting("codigo")
                .isEqualTo("PIX-422-CONTRATO-NAO-ASSINADO");
    }

    @Test
    void semAgenda_naoProcessavel() {
        when(repository.findByIdempotencyKey("idem-1")).thenReturn(Optional.empty());
        stubInelegivel(MotivoInelegibilidade.AGENDA_INEXISTENTE);

        assertThatThrownBy(() -> useCase.executar(comando(VALOR, "idem-1")))
                .isInstanceOf(OperacaoNaoProcessavelException.class)
                .extracting("codigo")
                .isEqualTo("PIX-422-AGENDA-INEXISTENTE");
    }

    @Test
    void escrowInoperante_naoProcessavel() {
        when(repository.findByIdempotencyKey("idem-1")).thenReturn(Optional.empty());
        stubInelegivel(MotivoInelegibilidade.ESCROW_INOPERANTE);

        assertThatThrownBy(() -> useCase.executar(comando(VALOR, "idem-1")))
                .isInstanceOf(OperacaoNaoProcessavelException.class)
                .extracting("codigo")
                .isEqualTo("PIX-422-ESCROW-INOPERANTE");
    }

    @Test
    void valorDivergeDoContrato_naoProcessavel() {
        when(repository.findByIdempotencyKey("idem-1")).thenReturn(Optional.empty());
        stubElegivel();

        assertThatThrownBy(() -> useCase.executar(comando(new BigDecimal("9999.00"), "idem-1")))
                .isInstanceOf(OperacaoNaoProcessavelException.class)
                .extracting("codigo")
                .isEqualTo("PIX-422-VALOR-DIVERGENTE");
    }

    @Test
    void contratoJaComDesembolsoOcupado_conflito() {
        when(repository.findByIdempotencyKey("idem-1")).thenReturn(Optional.empty());
        stubElegivel();
        PixTransferencia ocupando = PixTransferencia.criarDesembolso(
                contratoId, propostaId, tomadorId, VALOR, "h".repeat(64), "m", "idem-anterior", "corr-0");
        when(repository.findFirstByContratoIdAndStatusInOrderByDataCriacaoDesc(eq(contratoId), anyCollection()))
                .thenReturn(Optional.of(ocupando));

        assertThatThrownBy(() -> useCase.executar(comando(VALOR, "idem-1")))
                .isInstanceOf(ConflitoException.class)
                .extracting("codigo")
                .isEqualTo("PIX-409-DESEMBOLSO-DUPLICADO");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void corridaConcorrente_devolveConflitoSemReconsultarNaTxEnvenenada() {
        when(repository.findByIdempotencyKey("idem-1")).thenReturn(Optional.empty());
        stubElegivel();
        when(repository.findFirstByContratoIdAndStatusInOrderByDataCriacaoDesc(eq(contratoId), anyCollection()))
                .thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("unique"));

        assertThatThrownBy(() -> useCase.executar(comando(VALOR, "idem-1")))
                .isInstanceOf(ConflitoException.class)
                .extracting("codigo")
                .isEqualTo("PIX-409-CONFLITO-CONCORRENTE");

        // Nao reconsulta na transacao ja marcada rollback-only: apenas o pre-check inicial.
        verify(repository).findByIdempotencyKey("idem-1");
    }
}
