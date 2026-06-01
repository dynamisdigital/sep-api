package com.dynamis.sep_api.backoffice.infrastructure.adapter.pix;

import com.dynamis.sep_api.backoffice.application.dto.ObjetoOriginalResumo;
import com.dynamis.sep_api.backoffice.domain.vo.TipoEntidadeReferenciada;
import com.dynamis.sep_api.pix.domain.model.PixTransferencia;
import com.dynamis.sep_api.pix.infrastructure.persistence.PixTransferenciaRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PixTransferenciaObjetoOriginalAdapterTest {

    private final PixTransferenciaRepository repository = mock(PixTransferenciaRepository.class);
    private final PixTransferenciaObjetoOriginalAdapter adapter = new PixTransferenciaObjetoOriginalAdapter(repository);

    @Test
    void tipoSuportado_ehPixTransferencia() {
        assertThat(adapter.tipoSuportado()).isEqualTo(TipoEntidadeReferenciada.PIX_TRANSFERENCIA);
    }

    @Test
    void buscar_transferenciaPresente_resolveResumoSemChaveEmClaro() {
        PixTransferencia t = PixTransferencia.criarDesembolso(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("10000.00"),
                "h".repeat(64),
                "us****om",
                "idem-1",
                "corr-1");
        when(repository.findById(t.getId())).thenReturn(Optional.of(t));

        Optional<ObjetoOriginalResumo> resumo = adapter.buscar(t.getId());

        assertThat(resumo).isPresent();
        assertThat(resumo.get().tipoEntidade()).isEqualTo(TipoEntidadeReferenciada.PIX_TRANSFERENCIA);
        assertThat(resumo.get().status()).isEqualTo("CRIADA");
        assertThat(resumo.get().descricaoCurta()).contains("us****om");
    }

    @Test
    void buscar_transferenciaAusente_vazio() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThat(adapter.buscar(id)).isEmpty();
    }
}
