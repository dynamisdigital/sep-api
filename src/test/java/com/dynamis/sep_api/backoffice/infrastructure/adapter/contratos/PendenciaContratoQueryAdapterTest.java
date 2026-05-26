package com.dynamis.sep_api.backoffice.infrastructure.adapter.contratos;

import com.dynamis.sep_api.backoffice.application.port.out.dto.ContratoPendenciaView;
import com.dynamis.sep_api.contratos.domain.model.Contrato;
import com.dynamis.sep_api.contratos.domain.vo.StatusFormalizacao;
import com.dynamis.sep_api.contratos.domain.vo.TipoContrato;
import com.dynamis.sep_api.contratos.infrastructure.persistence.ContratoRepository;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PendenciaContratoQueryAdapterTest {

    @Test
    void mapeiaParaProjecaoMinima() {
        ContratoRepository repo = mock(ContratoRepository.class);
        Contrato c = Contrato.criar(UUID.randomUUID(), UUID.randomUUID(), TipoContrato.MUTUO);
        when(repo.findByStatusAndDataModificacaoBefore(eq(StatusFormalizacao.ACEITO), any()))
                .thenReturn(List.of(c));

        List<ContratoPendenciaView> result =
                new PendenciaContratoQueryAdapter(repo).contratosAceitosSemAssinatura(OffsetDateTime.now());

        assertThat(result).extracting(ContratoPendenciaView::contratoId).containsExactly(c.getId());
    }
}
