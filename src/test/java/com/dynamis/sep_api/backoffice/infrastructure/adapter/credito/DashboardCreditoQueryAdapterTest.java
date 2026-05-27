package com.dynamis.sep_api.backoffice.infrastructure.adapter.credito;

import com.dynamis.sep_api.backoffice.application.dto.ContadorPorStatusProposta;
import com.dynamis.sep_api.credito.domain.vo.StatusProposta;
import com.dynamis.sep_api.credito.infrastructure.persistence.PropostaCreditoRepository;
import com.dynamis.sep_api.credito.infrastructure.persistence.PropostaCreditoRepository.StatusContagemView;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DashboardCreditoQueryAdapterTest {

    @Test
    void contagemPorStatus_mapeiaParaDto() {
        PropostaCreditoRepository repo = mock(PropostaCreditoRepository.class);
        StatusContagemView v = mock(StatusContagemView.class);
        when(v.getStatus()).thenReturn(StatusProposta.EM_ANALISE);
        when(v.getTotal()).thenReturn(5L);
        when(repo.contarPorStatus()).thenReturn(List.of(v));

        List<ContadorPorStatusProposta> r = new DashboardCreditoQueryAdapter(repo).contagemPorStatus();

        assertThat(r).hasSize(1);
        assertThat(r.get(0).status()).isEqualTo("EM_ANALISE");
        assertThat(r.get(0).total()).isEqualTo(5L);
    }
}
