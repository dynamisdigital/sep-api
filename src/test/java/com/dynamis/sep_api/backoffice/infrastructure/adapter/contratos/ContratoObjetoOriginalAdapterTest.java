package com.dynamis.sep_api.backoffice.infrastructure.adapter.contratos;

import com.dynamis.sep_api.backoffice.application.dto.ObjetoOriginalResumo;
import com.dynamis.sep_api.backoffice.domain.vo.TipoEntidadeReferenciada;
import com.dynamis.sep_api.contratos.domain.model.Contrato;
import com.dynamis.sep_api.contratos.domain.vo.TipoContrato;
import com.dynamis.sep_api.contratos.infrastructure.persistence.ContratoRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ContratoObjetoOriginalAdapterTest {

    @Test
    void tipoSuportado_eContrato() {
        assertThat(new ContratoObjetoOriginalAdapter(mock(ContratoRepository.class)).tipoSuportado())
                .isEqualTo(TipoEntidadeReferenciada.CONTRATO);
    }

    @Test
    void buscar_existente_devolveResumo() {
        ContratoRepository repo = mock(ContratoRepository.class);
        Contrato c = Contrato.criar(UUID.randomUUID(), UUID.randomUUID(), TipoContrato.MUTUO);
        when(repo.findById(c.getId())).thenReturn(Optional.of(c));

        Optional<ObjetoOriginalResumo> resumo = new ContratoObjetoOriginalAdapter(repo).buscar(c.getId());

        assertThat(resumo).isPresent();
        assertThat(resumo.get().status()).isEqualTo(c.getStatus().name());
        assertThat(resumo.get().descricaoCurta()).contains(c.getPropostaId().toString());
    }
}
