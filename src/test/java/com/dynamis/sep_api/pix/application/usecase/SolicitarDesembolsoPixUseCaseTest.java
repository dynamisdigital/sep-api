package com.dynamis.sep_api.pix.application.usecase;

import com.dynamis.sep_api.pix.application.dto.SolicitarDesembolsoPixCommand;
import com.dynamis.sep_api.pix.application.dto.SolicitarDesembolsoPixResult;
import com.dynamis.sep_api.pix.application.port.out.dto.AgendaDesembolsoView;
import com.dynamis.sep_api.pix.application.port.out.dto.ContratoDesembolsoView;
import com.dynamis.sep_api.pix.application.port.out.dto.EscrowDesembolsoView;
import com.dynamis.sep_api.pix.application.service.ChavePixSeguranca;
import com.dynamis.sep_api.pix.application.service.ResultadoElegibilidadeDesembolso;
import com.dynamis.sep_api.pix.application.service.ResultadoElegibilidadeDesembolso.MotivoInelegibilidade;
import com.dynamis.sep_api.pix.application.service.ValidadorElegibilidadeDesembolso;
import com.dynamis.sep_api.pix.domain.model.PixTransferencia;
import com.dynamis.sep_api.pix.domain.vo.StatusPixTransferencia;
import com.dynamis.sep_api.pix.infrastructure.persistence.PixTransferenciaRepository;
import com.dynamis.sep_api.shared.exception.ConflitoException;
import com.dynamis.sep_api.shared.exception.OperacaoNaoProcessavelException;
import com.dynamis.sep_api.shared.exception.RecursoNaoEncontradoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
        useCase = new SolicitarDesembolsoPixUseCase(repository, validador);
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

    @Test
    void contratoElegivel_criaTransferenciaCriada() {
        when(repository.findByIdempotencyKey("idem-1")).thenReturn(Optional.empty());
        stubElegivel();
        when(repository.findFirstByContratoIdAndStatusInOrderByDataCriacaoDesc(eq(contratoId), anyCollection()))
                .thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        SolicitarDesembolsoPixResult res = useCase.executar(comando(VALOR, "idem-1"));

        assertThat(res.novo()).isTrue();
        assertThat(res.status()).isEqualTo(StatusPixTransferencia.CRIADA);
        assertThat(res.contratoId()).isEqualTo(contratoId);
        assertThat(res.chaveDestinoMascara()).contains("*").doesNotContain("empresa");
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
    void corridaConcorrenteMesmaKey_resolveIdempotente() {
        when(repository.findByIdempotencyKey("idem-1")).thenReturn(Optional.empty());
        stubElegivel();
        when(repository.findFirstByContratoIdAndStatusInOrderByDataCriacaoDesc(eq(contratoId), anyCollection()))
                .thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("unique"));
        PixTransferencia vencedora = PixTransferencia.criarDesembolso(
                contratoId,
                propostaId,
                tomadorId,
                VALOR,
                ChavePixSeguranca.hashHex(CHAVE),
                ChavePixSeguranca.mascarar(CHAVE),
                "idem-1",
                "corr-1");
        when(repository.findByIdempotencyKey("idem-1"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(vencedora));

        SolicitarDesembolsoPixResult res = useCase.executar(comando(VALOR, "idem-1"));

        assertThat(res.novo()).isFalse();
        assertThat(res.transferenciaId()).isEqualTo(vencedora.getId());
    }
}
