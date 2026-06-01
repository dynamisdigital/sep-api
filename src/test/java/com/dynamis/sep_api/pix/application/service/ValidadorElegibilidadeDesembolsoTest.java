package com.dynamis.sep_api.pix.application.service;

import com.dynamis.sep_api.pix.application.port.out.CobrancaDesembolsoQueryPort;
import com.dynamis.sep_api.pix.application.port.out.ContratoDesembolsoQueryPort;
import com.dynamis.sep_api.pix.application.port.out.EscrowDesembolsoQueryPort;
import com.dynamis.sep_api.pix.application.port.out.dto.AgendaDesembolsoView;
import com.dynamis.sep_api.pix.application.port.out.dto.ContratoDesembolsoView;
import com.dynamis.sep_api.pix.application.port.out.dto.EscrowDesembolsoView;
import com.dynamis.sep_api.pix.application.service.ResultadoElegibilidadeDesembolso.MotivoInelegibilidade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ValidadorElegibilidadeDesembolsoTest {

    private ContratoDesembolsoQueryPort contratoPort;
    private CobrancaDesembolsoQueryPort cobrancaPort;
    private EscrowDesembolsoQueryPort escrowPort;
    private ValidadorElegibilidadeDesembolso validador;

    private final UUID contratoId = UUID.randomUUID();
    private final UUID propostaId = UUID.randomUUID();
    private final UUID tomadorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        contratoPort = mock(ContratoDesembolsoQueryPort.class);
        cobrancaPort = mock(CobrancaDesembolsoQueryPort.class);
        escrowPort = mock(EscrowDesembolsoQueryPort.class);
        validador = new ValidadorElegibilidadeDesembolso(contratoPort, cobrancaPort, escrowPort);
    }

    private ContratoDesembolsoView contrato(boolean assinado) {
        return new ContratoDesembolsoView(contratoId, propostaId, tomadorId, new BigDecimal("10000.00"), assinado);
    }

    @Test
    void contratoAssinadoComAgendaEEscrowOperacional_eElegivel() {
        ContratoDesembolsoView contrato = contrato(true);
        AgendaDesembolsoView agenda = new AgendaDesembolsoView(contratoId, UUID.randomUUID(), 12);
        EscrowDesembolsoView escrow = new EscrowDesembolsoView(propostaId, UUID.randomUUID(), "ext-1", true);
        when(contratoPort.buscarPorContrato(contratoId)).thenReturn(Optional.of(contrato));
        when(cobrancaPort.buscarAgendaAtivaPorContrato(contratoId)).thenReturn(Optional.of(agenda));
        when(escrowPort.buscarPorProposta(propostaId)).thenReturn(Optional.of(escrow));

        ResultadoElegibilidadeDesembolso resultado = validador.validar(contratoId);

        assertThat(resultado.elegivel()).isTrue();
        assertThat(resultado.motivo()).isNull();
        assertThat(resultado.contrato()).isSameAs(contrato);
        assertThat(resultado.agenda()).isSameAs(agenda);
        assertThat(resultado.escrow()).isSameAs(escrow);
    }

    @Test
    void contratoInexistente_eInelegivel() {
        when(contratoPort.buscarPorContrato(contratoId)).thenReturn(Optional.empty());

        ResultadoElegibilidadeDesembolso resultado = validador.validar(contratoId);

        assertThat(resultado.elegivel()).isFalse();
        assertThat(resultado.motivo()).isEqualTo(MotivoInelegibilidade.CONTRATO_NAO_ENCONTRADO);
    }

    @Test
    void contratoNaoAssinado_eInelegivel() {
        when(contratoPort.buscarPorContrato(contratoId)).thenReturn(Optional.of(contrato(false)));

        ResultadoElegibilidadeDesembolso resultado = validador.validar(contratoId);

        assertThat(resultado.elegivel()).isFalse();
        assertThat(resultado.motivo()).isEqualTo(MotivoInelegibilidade.CONTRATO_NAO_ASSINADO);
    }

    @Test
    void semAgendaAtiva_eInelegivel() {
        when(contratoPort.buscarPorContrato(contratoId)).thenReturn(Optional.of(contrato(true)));
        when(cobrancaPort.buscarAgendaAtivaPorContrato(contratoId)).thenReturn(Optional.empty());

        ResultadoElegibilidadeDesembolso resultado = validador.validar(contratoId);

        assertThat(resultado.elegivel()).isFalse();
        assertThat(resultado.motivo()).isEqualTo(MotivoInelegibilidade.AGENDA_INEXISTENTE);
    }

    @Test
    void semEscrow_eInelegivel() {
        when(contratoPort.buscarPorContrato(contratoId)).thenReturn(Optional.of(contrato(true)));
        when(cobrancaPort.buscarAgendaAtivaPorContrato(contratoId))
                .thenReturn(Optional.of(new AgendaDesembolsoView(contratoId, UUID.randomUUID(), 12)));
        when(escrowPort.buscarPorProposta(propostaId)).thenReturn(Optional.empty());

        ResultadoElegibilidadeDesembolso resultado = validador.validar(contratoId);

        assertThat(resultado.elegivel()).isFalse();
        assertThat(resultado.motivo()).isEqualTo(MotivoInelegibilidade.ESCROW_INOPERANTE);
    }

    @Test
    void escrowNaoOperacional_eInelegivel() {
        when(contratoPort.buscarPorContrato(contratoId)).thenReturn(Optional.of(contrato(true)));
        when(cobrancaPort.buscarAgendaAtivaPorContrato(contratoId))
                .thenReturn(Optional.of(new AgendaDesembolsoView(contratoId, UUID.randomUUID(), 12)));
        when(escrowPort.buscarPorProposta(propostaId))
                .thenReturn(Optional.of(new EscrowDesembolsoView(propostaId, UUID.randomUUID(), null, false)));

        ResultadoElegibilidadeDesembolso resultado = validador.validar(contratoId);

        assertThat(resultado.elegivel()).isFalse();
        assertThat(resultado.motivo()).isEqualTo(MotivoInelegibilidade.ESCROW_INOPERANTE);
    }
}
