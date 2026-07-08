package com.dynamis.sep_api.cobranca.application.usecase;

import com.dynamis.sep_api.cobranca.application.dto.RenegociacaoTomadorResult;
import com.dynamis.sep_api.cobranca.application.port.out.ContratoCobrancaQueryPort;
import com.dynamis.sep_api.cobranca.application.port.out.ParcelaCobrancaPort;
import com.dynamis.sep_api.cobranca.application.port.out.RenegociacaoCobrancaPort;
import com.dynamis.sep_api.cobranca.domain.exception.CobrancaOwnershipException;
import com.dynamis.sep_api.cobranca.domain.exception.RenegociacaoNaoEncontradaException;
import com.dynamis.sep_api.cobranca.domain.model.AgendaPagamento;
import com.dynamis.sep_api.cobranca.domain.model.AgendaPagamento.ParcelaPlanejada;
import com.dynamis.sep_api.cobranca.domain.model.ParcelaCobranca;
import com.dynamis.sep_api.cobranca.domain.model.Renegociacao;
import com.dynamis.sep_api.cobranca.domain.vo.ComposicaoValor;
import com.dynamis.sep_api.cobranca.domain.vo.StatusParcela;
import com.dynamis.sep_api.cobranca.domain.vo.StatusRenegociacao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ConsultarRenegociacaoAtivaTomadorUseCaseTest {

    private static final Instant AGORA = Instant.parse("2026-06-15T12:00:00Z");
    private static final OffsetDateTime AGORA_ODT = OffsetDateTime.ofInstant(AGORA, ZoneOffset.UTC);

    private ParcelaCobrancaPort parcelaPort;
    private ContratoCobrancaQueryPort contratoQueryPort;
    private RenegociacaoCobrancaPort renegociacaoPort;
    private ConsultarRenegociacaoAtivaTomadorUseCase useCase;

    @BeforeEach
    void setup() {
        parcelaPort = mock(ParcelaCobrancaPort.class);
        contratoQueryPort = mock(ContratoCobrancaQueryPort.class);
        renegociacaoPort = mock(RenegociacaoCobrancaPort.class);
        useCase = new ConsultarRenegociacaoAtivaTomadorUseCase(
                parcelaPort, contratoQueryPort, renegociacaoPort, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    @Test
    void ownerComPropostaFutura_recebeTermosComValorTotalCalculadoNoBackend() {
        UUID contratoId = UUID.randomUUID();
        UUID tomadorId = UUID.randomUUID();
        ParcelaCobranca parcela = novaParcela(contratoId);
        UUID parcelaId = parcela.getId();
        Renegociacao ativa = proposta(parcelaId, AGORA_ODT.minusDays(1), AGORA_ODT.plusDays(7));
        stubOwner(parcela, contratoId, tomadorId);
        when(renegociacaoPort.buscarPorParcelaOriginalEStatus(parcelaId, StatusRenegociacao.PROPOSTA))
                .thenReturn(Optional.of(ativa));

        RenegociacaoTomadorResult res = useCase.executar(parcelaId, tomadorId);

        assertThat(res.renegociacaoId()).isEqualTo(ativa.getId());
        assertThat(res.parcelaId()).isEqualTo(parcelaId);
        assertThat(res.status()).isEqualTo(StatusRenegociacao.PROPOSTA);
        assertThat(res.novoValorParcela()).isEqualByComparingTo("200.00");
        assertThat(res.numeroParcelas()).isEqualTo(3);
        // 200.00 * 3 — total nunca vem do mobile.
        assertThat(res.valorTotalRenegociado()).isEqualByComparingTo("600.00");
        assertThat(res.novoVencimento()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(res.desconto()).isEqualByComparingTo("50.00");
        assertThat(res.dataProposta()).isEqualTo(ativa.getDataProposta());
        assertThat(res.dataExpiracao()).isEqualTo(ativa.getDataExpiracao());
    }

    @Test
    void consultaUsaSomenteStatusProposta_estadosFinaisNaoSaoAtivos() {
        UUID contratoId = UUID.randomUUID();
        UUID tomadorId = UUID.randomUUID();
        ParcelaCobranca parcela = novaParcela(contratoId);
        UUID parcelaId = parcela.getId();
        stubOwner(parcela, contratoId, tomadorId);
        // Sem stub -> Mockito devolve Optional.empty(): nenhuma PROPOSTA ativa.
        assertThatThrownBy(() -> useCase.executar(parcelaId, tomadorId))
                .isInstanceOf(RenegociacaoNaoEncontradaException.class);

        // Prova que ACEITA/RECUSADA/EXPIRADA nunca sao retornadas: a consulta filtra por PROPOSTA.
        verify(renegociacaoPort).buscarPorParcelaOriginalEStatus(parcelaId, StatusRenegociacao.PROPOSTA);
    }

    @Test
    void propostaComExpiracaoIgualAoAgora_naoEhAtiva() {
        UUID contratoId = UUID.randomUUID();
        UUID tomadorId = UUID.randomUUID();
        ParcelaCobranca parcela = novaParcela(contratoId);
        UUID parcelaId = parcela.getId();
        Renegociacao noInstante = proposta(parcelaId, AGORA_ODT.minusDays(1), AGORA_ODT);
        stubOwner(parcela, contratoId, tomadorId);
        when(renegociacaoPort.buscarPorParcelaOriginalEStatus(parcelaId, StatusRenegociacao.PROPOSTA))
                .thenReturn(Optional.of(noInstante));

        assertThatThrownBy(() -> useCase.executar(parcelaId, tomadorId))
                .isInstanceOf(RenegociacaoNaoEncontradaException.class);
    }

    @Test
    void propostaVencidaAntesDoJob_naoEhAtiva() {
        UUID contratoId = UUID.randomUUID();
        UUID tomadorId = UUID.randomUUID();
        ParcelaCobranca parcela = novaParcela(contratoId);
        UUID parcelaId = parcela.getId();
        Renegociacao vencida = proposta(parcelaId, AGORA_ODT.minusDays(8), AGORA_ODT.minusMinutes(1));
        stubOwner(parcela, contratoId, tomadorId);
        when(renegociacaoPort.buscarPorParcelaOriginalEStatus(parcelaId, StatusRenegociacao.PROPOSTA))
                .thenReturn(Optional.of(vencida));

        assertThatThrownBy(() -> useCase.executar(parcelaId, tomadorId))
                .isInstanceOf(RenegociacaoNaoEncontradaException.class);
    }

    @Test
    void parcelaInexistente_ownershipException_semConsultarContratoNemRenegociacao() {
        UUID parcelaId = UUID.randomUUID();
        when(parcelaPort.buscarPorId(parcelaId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(parcelaId, UUID.randomUUID()))
                .isInstanceOf(CobrancaOwnershipException.class);

        verifyNoInteractions(contratoQueryPort);
        verifyNoInteractions(renegociacaoPort);
    }

    @Test
    void parcelaAlheia_ownershipException_semConsultarRenegociacao() {
        UUID contratoId = UUID.randomUUID();
        ParcelaCobranca parcela = novaParcela(contratoId);
        UUID parcelaId = parcela.getId();
        when(parcelaPort.buscarPorId(parcelaId)).thenReturn(Optional.of(parcela));
        when(contratoQueryPort.tomadorIdDoContrato(contratoId)).thenReturn(Optional.of(UUID.randomUUID()));

        assertThatThrownBy(() -> useCase.executar(parcelaId, UUID.randomUUID()))
                .isInstanceOf(CobrancaOwnershipException.class);

        verifyNoInteractions(renegociacaoPort);
    }

    @Test
    void contratoSemTomador_ownershipException_semConsultarRenegociacao() {
        UUID contratoId = UUID.randomUUID();
        ParcelaCobranca parcela = novaParcela(contratoId);
        UUID parcelaId = parcela.getId();
        when(parcelaPort.buscarPorId(parcelaId)).thenReturn(Optional.of(parcela));
        when(contratoQueryPort.tomadorIdDoContrato(contratoId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(parcelaId, UUID.randomUUID()))
                .isInstanceOf(CobrancaOwnershipException.class);

        verifyNoInteractions(renegociacaoPort);
    }

    private void stubOwner(ParcelaCobranca parcela, UUID contratoId, UUID tomadorId) {
        when(parcelaPort.buscarPorId(parcela.getId())).thenReturn(Optional.of(parcela));
        when(contratoQueryPort.tomadorIdDoContrato(contratoId)).thenReturn(Optional.of(tomadorId));
    }

    private static ParcelaCobranca novaParcela(UUID contratoId) {
        AgendaPagamento agenda = AgendaPagamento.criar(
                contratoId,
                List.of(new ParcelaPlanejada(
                        1, ComposicaoValor.principalApenas(new BigDecimal("1000.00")), LocalDate.of(2026, 6, 1))));
        return agenda.getParcelas().get(0);
    }

    private static Renegociacao proposta(UUID parcelaId, OffsetDateTime dataProposta, OffsetDateTime dataExpiracao) {
        return Renegociacao.propor(
                parcelaId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                StatusParcela.ATRASADA,
                new BigDecimal("200.00"),
                LocalDate.of(2026, 7, 1),
                3,
                new BigDecimal("50.00"),
                "acordo de renegociacao",
                UUID.randomUUID(),
                dataProposta,
                dataExpiracao);
    }
}
