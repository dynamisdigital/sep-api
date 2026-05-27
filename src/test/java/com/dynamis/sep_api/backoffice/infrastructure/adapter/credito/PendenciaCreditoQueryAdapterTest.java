package com.dynamis.sep_api.backoffice.infrastructure.adapter.credito;

import com.dynamis.sep_api.backoffice.application.port.out.dto.PropostaPendenciaView;
import com.dynamis.sep_api.credito.domain.model.PropostaCredito;
import com.dynamis.sep_api.credito.domain.vo.Money;
import com.dynamis.sep_api.credito.domain.vo.StatusProposta;
import com.dynamis.sep_api.credito.domain.vo.TipoOperacao;
import com.dynamis.sep_api.credito.infrastructure.persistence.PropostaCreditoRepository;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PendenciaCreditoQueryAdapterTest {

    @Test
    void mapeiaParaProjecaoMinima() {
        PropostaCreditoRepository repo = mock(PropostaCreditoRepository.class);
        PropostaCredito p = PropostaCredito.criar(
                UUID.randomUUID(), UUID.randomUUID(), TipoOperacao.CAPITAL_GIRO, Money.brl("1000"), 6);
        when(repo.findByStatusAndDataModificacaoBefore(eq(StatusProposta.EM_ANALISE), any()))
                .thenReturn(List.of(p));

        List<PropostaPendenciaView> result =
                new PendenciaCreditoQueryAdapter(repo).propostasParadasEmAnalise(OffsetDateTime.now());

        assertThat(result).extracting(PropostaPendenciaView::propostaId).containsExactly(p.getId());
    }
}
