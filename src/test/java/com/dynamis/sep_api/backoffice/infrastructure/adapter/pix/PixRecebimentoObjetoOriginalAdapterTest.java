package com.dynamis.sep_api.backoffice.infrastructure.adapter.pix;

import com.dynamis.sep_api.backoffice.application.dto.ObjetoOriginalResumo;
import com.dynamis.sep_api.backoffice.domain.vo.TipoEntidadeReferenciada;
import com.dynamis.sep_api.pix.domain.model.PixRecebimento;
import com.dynamis.sep_api.pix.infrastructure.persistence.PixRecebimentoRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PixRecebimentoObjetoOriginalAdapterTest {

    private final PixRecebimentoRepository repository = mock(PixRecebimentoRepository.class);
    private final PixRecebimentoObjetoOriginalAdapter adapter = new PixRecebimentoObjetoOriginalAdapter(repository);

    @Test
    void tipoSuportado_ehPixRecebimento() {
        assertThat(adapter.tipoSuportado()).isEqualTo(TipoEntidadeReferenciada.PIX_RECEBIMENTO);
    }

    @Test
    void buscar_recebimentoPresente_resolveResumoComValorEStatus() {
        PixRecebimento r = PixRecebimento.registrar("E2E-1", new BigDecimal("250.00"), OffsetDateTime.now(), "corr-1");
        when(repository.findById(r.getId())).thenReturn(Optional.of(r));

        Optional<ObjetoOriginalResumo> resumo = adapter.buscar(r.getId());

        assertThat(resumo).isPresent();
        assertThat(resumo.get().tipoEntidade()).isEqualTo(TipoEntidadeReferenciada.PIX_RECEBIMENTO);
        assertThat(resumo.get().status()).isEqualTo("RECEBIDO");
        assertThat(resumo.get().descricaoCurta()).contains("250.00");
    }

    @Test
    void buscar_recebimentoAusente_vazio() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThat(adapter.buscar(id)).isEmpty();
    }
}
