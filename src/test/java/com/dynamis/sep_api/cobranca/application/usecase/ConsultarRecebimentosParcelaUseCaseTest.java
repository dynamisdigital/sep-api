package com.dynamis.sep_api.cobranca.application.usecase;

import com.dynamis.sep_api.cobranca.application.dto.RecebimentoTomadorResult;
import com.dynamis.sep_api.cobranca.application.port.out.ContratoCobrancaQueryPort;
import com.dynamis.sep_api.cobranca.domain.exception.CobrancaOwnershipException;
import com.dynamis.sep_api.cobranca.domain.model.AgendaPagamento;
import com.dynamis.sep_api.cobranca.domain.model.AgendaPagamento.ParcelaPlanejada;
import com.dynamis.sep_api.cobranca.domain.model.ParcelaCobranca;
import com.dynamis.sep_api.cobranca.domain.model.Recebimento;
import com.dynamis.sep_api.cobranca.domain.vo.ComposicaoValor;
import com.dynamis.sep_api.cobranca.infrastructure.persistence.ParcelaCobrancaRepository;
import com.dynamis.sep_api.cobranca.infrastructure.persistence.RecebimentoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ConsultarRecebimentosParcelaUseCaseTest {

    private ParcelaCobrancaRepository parcelaRepository;
    private ContratoCobrancaQueryPort contratoQueryPort;
    private RecebimentoRepository recebimentoRepository;
    private ConsultarRecebimentosParcelaUseCase useCase;

    @BeforeEach
    void setup() {
        parcelaRepository = mock(ParcelaCobrancaRepository.class);
        contratoQueryPort = mock(ContratoCobrancaQueryPort.class);
        recebimentoRepository = mock(RecebimentoRepository.class);
        useCase = new ConsultarRecebimentosParcelaUseCase(parcelaRepository, contratoQueryPort, recebimentoRepository);
    }

    @Test
    void ownerRecebeHistorico() {
        UUID contratoId = UUID.randomUUID();
        UUID tomadorId = UUID.randomUUID();
        ParcelaCobranca parcela = novaParcela(contratoId);
        Recebimento r1 = recebimento(parcela, "100.00", "k1");
        Recebimento r2 = recebimento(parcela, "200.00", "k2");
        UUID parcelaId = parcela.getId();
        when(parcelaRepository.findById(parcelaId)).thenReturn(Optional.of(parcela));
        when(contratoQueryPort.tomadorIdDoContrato(contratoId)).thenReturn(Optional.of(tomadorId));
        // repository e responsavel pela ordenacao DESC — aqui devolvemos ja ordenado.
        when(recebimentoRepository.findByParcela_IdOrderByDataRecebimentoDesc(parcelaId))
                .thenReturn(List.of(r2, r1));

        List<RecebimentoTomadorResult> resultado = useCase.executar(parcelaId, tomadorId);

        assertThat(resultado).hasSize(2);
        assertThat(resultado.get(0).recebimentoId()).isEqualTo(r2.getId());
        assertThat(resultado.get(1).recebimentoId()).isEqualTo(r1.getId());
    }

    @Test
    void ownerSemRecebimentos_listaVazia() {
        UUID contratoId = UUID.randomUUID();
        UUID tomadorId = UUID.randomUUID();
        ParcelaCobranca parcela = novaParcela(contratoId);
        UUID parcelaId = parcela.getId();
        when(parcelaRepository.findById(parcelaId)).thenReturn(Optional.of(parcela));
        when(contratoQueryPort.tomadorIdDoContrato(contratoId)).thenReturn(Optional.of(tomadorId));
        when(recebimentoRepository.findByParcela_IdOrderByDataRecebimentoDesc(parcelaId))
                .thenReturn(List.of());

        assertThat(useCase.executar(parcelaId, tomadorId)).isEmpty();
    }

    @Test
    void parcelaInexistente_lancaOwnershipException_semConsultarContratoOuRecebimentos() {
        UUID parcelaId = UUID.randomUUID();
        when(parcelaRepository.findById(parcelaId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(parcelaId, UUID.randomUUID()))
                .isInstanceOf(CobrancaOwnershipException.class);

        verifyNoInteractions(contratoQueryPort);
        verifyNoInteractions(recebimentoRepository);
    }

    @Test
    void parcelaAlheia_lancaOwnershipException_semConsultarRecebimentos() {
        UUID contratoId = UUID.randomUUID();
        ParcelaCobranca parcela = novaParcela(contratoId);
        UUID parcelaId = parcela.getId();
        when(parcelaRepository.findById(parcelaId)).thenReturn(Optional.of(parcela));
        when(contratoQueryPort.tomadorIdDoContrato(contratoId)).thenReturn(Optional.of(UUID.randomUUID()));

        assertThatThrownBy(() -> useCase.executar(parcelaId, UUID.randomUUID()))
                .isInstanceOf(CobrancaOwnershipException.class);

        verifyNoInteractions(recebimentoRepository);
    }

    @Test
    void contratoSemTomador_lancaOwnershipException_semConsultarRecebimentos() {
        UUID contratoId = UUID.randomUUID();
        ParcelaCobranca parcela = novaParcela(contratoId);
        UUID parcelaId = parcela.getId();
        when(parcelaRepository.findById(parcelaId)).thenReturn(Optional.of(parcela));
        when(contratoQueryPort.tomadorIdDoContrato(contratoId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(parcelaId, UUID.randomUUID()))
                .isInstanceOf(CobrancaOwnershipException.class);

        verifyNoInteractions(recebimentoRepository);
    }

    @Test
    void resultExpoeApenasCamposPublicos() {
        UUID contratoId = UUID.randomUUID();
        UUID tomadorId = UUID.randomUUID();
        ParcelaCobranca parcela = novaParcela(contratoId);
        Recebimento r = recebimento(parcela, "150.00", "k1");
        UUID parcelaId = parcela.getId();
        when(parcelaRepository.findById(parcelaId)).thenReturn(Optional.of(parcela));
        when(contratoQueryPort.tomadorIdDoContrato(contratoId)).thenReturn(Optional.of(tomadorId));
        when(recebimentoRepository.findByParcela_IdOrderByDataRecebimentoDesc(parcelaId))
                .thenReturn(List.of(r));

        RecebimentoTomadorResult res = useCase.executar(parcelaId, tomadorId).get(0);

        assertThat(res.recebimentoId()).isEqualTo(r.getId());
        assertThat(res.valorRecebido()).isEqualByComparingTo("150.00");
        assertThat(res.dataRecebimento()).isEqualTo(r.getDataRecebimento());
        assertThat(res.meioPagamento()).isEqualTo(r.getMeioPagamento());
    }

    private static ParcelaCobranca novaParcela(UUID contratoId) {
        AgendaPagamento agenda = AgendaPagamento.criar(
                contratoId,
                List.of(new ParcelaPlanejada(
                        1, ComposicaoValor.principalApenas(new BigDecimal("1000.00")), LocalDate.of(2026, 6, 1))));
        return agenda.getParcelas().get(0);
    }

    private static Recebimento recebimento(ParcelaCobranca parcela, String valor, String idempotencyKey) {
        return parcela.registrarRecebimento(
                new BigDecimal(valor),
                new BigDecimal("1000.00"),
                OffsetDateTime.now(),
                "TRANSFERENCIA",
                null,
                idempotencyKey,
                null,
                UUID.randomUUID());
    }
}
