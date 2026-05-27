package com.dynamis.sep_api.backoffice.infrastructure.adapter.credito;

import com.dynamis.sep_api.backoffice.application.dto.ObjetoOriginalResumo;
import com.dynamis.sep_api.backoffice.domain.vo.TipoEntidadeReferenciada;
import com.dynamis.sep_api.credito.domain.model.PropostaCredito;
import com.dynamis.sep_api.credito.domain.vo.Money;
import com.dynamis.sep_api.credito.domain.vo.TipoOperacao;
import com.dynamis.sep_api.credito.infrastructure.persistence.PropostaCreditoRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PropostaObjetoOriginalAdapterTest {

    @Test
    void tipoSuportado_eProposta() {
        assertThat(new PropostaObjetoOriginalAdapter(mock(PropostaCreditoRepository.class)).tipoSuportado())
                .isEqualTo(TipoEntidadeReferenciada.PROPOSTA);
    }

    @Test
    void buscar_existente_devolveResumoComStatus() {
        PropostaCreditoRepository repo = mock(PropostaCreditoRepository.class);
        PropostaCredito p = PropostaCredito.criar(
                UUID.randomUUID(), UUID.randomUUID(), TipoOperacao.CAPITAL_GIRO, Money.brl("5000"), 12);
        when(repo.findById(p.getId())).thenReturn(Optional.of(p));

        Optional<ObjetoOriginalResumo> resumo = new PropostaObjetoOriginalAdapter(repo).buscar(p.getId());

        assertThat(resumo).isPresent();
        assertThat(resumo.get().status()).isEqualTo(p.getStatus().name());
        assertThat(resumo.get().descricaoCurta()).contains("CAPITAL_GIRO").contains("5000");
    }
}
