package com.dynamis.sep_api.backoffice.infrastructure.adapter.cobranca;

import com.dynamis.sep_api.backoffice.application.dto.InadimplenciaConsolidada;
import com.dynamis.sep_api.cobranca.infrastructure.persistence.ParcelaCobrancaRepository;
import com.dynamis.sep_api.cobranca.infrastructure.persistence.RecebimentoRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DashboardCobrancaQueryAdapterTest {

    @Test
    void recebimentosNoDia_passaIntervaloUtc() {
        RecebimentoRepository recebimento = mock(RecebimentoRepository.class);
        ParcelaCobrancaRepository parcela = mock(ParcelaCobrancaRepository.class);
        when(recebimento.somarValorRecebidoNoIntervalo(any(), any())).thenReturn(new BigDecimal("500"));

        BigDecimal r = new DashboardCobrancaQueryAdapter(recebimento, parcela)
                .recebimentosNoDia(LocalDate.of(2026, 5, 26));

        assertThat(r).isEqualByComparingTo("500");
    }

    @Test
    void recebimentosNoDia_nullVoltaZero() {
        RecebimentoRepository recebimento = mock(RecebimentoRepository.class);
        ParcelaCobrancaRepository parcela = mock(ParcelaCobrancaRepository.class);
        when(recebimento.somarValorRecebidoNoIntervalo(any(), any())).thenReturn(null);

        BigDecimal r = new DashboardCobrancaQueryAdapter(recebimento, parcela)
                .recebimentosNoDia(LocalDate.of(2026, 5, 26));

        assertThat(r).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void inadimplenciaTotal_mapeiaProjection() {
        RecebimentoRepository recebimento = mock(RecebimentoRepository.class);
        ParcelaCobrancaRepository parcela = mock(ParcelaCobrancaRepository.class);
        ParcelaCobrancaRepository.ResumoInadimplenciaView view =
                mock(ParcelaCobrancaRepository.ResumoInadimplenciaView.class);
        when(view.getNumeroParcelas()).thenReturn(3L);
        when(view.getValorTotal()).thenReturn(new BigDecimal("9000"));
        when(parcela.resumoInadimplencia()).thenReturn(view);

        InadimplenciaConsolidada r = new DashboardCobrancaQueryAdapter(recebimento, parcela).inadimplenciaTotal();

        assertThat(r.numeroParcelas()).isEqualTo(3);
        assertThat(r.valorTotal()).isEqualByComparingTo("9000");
    }

    @Test
    void inadimplenciaTotal_nullVoltaVazia() {
        RecebimentoRepository recebimento = mock(RecebimentoRepository.class);
        ParcelaCobrancaRepository parcela = mock(ParcelaCobrancaRepository.class);
        when(parcela.resumoInadimplencia()).thenReturn(null);

        InadimplenciaConsolidada r = new DashboardCobrancaQueryAdapter(recebimento, parcela).inadimplenciaTotal();

        assertThat(r.numeroParcelas()).isZero();
        assertThat(r.valorTotal()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
