package com.dynamis.sep_api.backoffice.infrastructure.adapter.cobranca;

import com.dynamis.sep_api.backoffice.application.dto.ObjetoOriginalResumo;
import com.dynamis.sep_api.backoffice.domain.vo.TipoEntidadeReferenciada;
import com.dynamis.sep_api.cobranca.domain.model.ParcelaCobranca;
import com.dynamis.sep_api.cobranca.domain.vo.StatusParcela;
import com.dynamis.sep_api.cobranca.infrastructure.persistence.ParcelaCobrancaRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ParcelaCobrancaObjetoOriginalAdapterTest {

    @Test
    void tipoSuportado_eParcelaCobranca() {
        assertThat(new ParcelaCobrancaObjetoOriginalAdapter(mock(ParcelaCobrancaRepository.class)).tipoSuportado())
                .isEqualTo(TipoEntidadeReferenciada.PARCELA_COBRANCA);
    }

    @Test
    void buscar_existente_devolveResumoComNumeroEVencimento() {
        ParcelaCobrancaRepository repo = mock(ParcelaCobrancaRepository.class);
        UUID id = UUID.randomUUID();
        ParcelaCobranca p = mock(ParcelaCobranca.class);
        when(p.getId()).thenReturn(id);
        when(p.getNumero()).thenReturn(3);
        when(p.getDataVencimento()).thenReturn(LocalDate.of(2026, 8, 15));
        when(p.getStatus()).thenReturn(StatusParcela.ATRASADA);
        when(repo.findById(id)).thenReturn(Optional.of(p));

        Optional<ObjetoOriginalResumo> resumo = new ParcelaCobrancaObjetoOriginalAdapter(repo).buscar(id);

        assertThat(resumo).isPresent();
        assertThat(resumo.get().status()).isEqualTo("ATRASADA");
        assertThat(resumo.get().descricaoCurta()).contains("3").contains("2026-08-15");
    }
}
