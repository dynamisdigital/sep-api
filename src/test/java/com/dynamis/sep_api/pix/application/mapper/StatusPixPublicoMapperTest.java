package com.dynamis.sep_api.pix.application.mapper;

import com.dynamis.sep_api.pix.domain.vo.StatusPixPublico;
import com.dynamis.sep_api.pix.domain.vo.StatusPixTransferencia;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StatusPixPublicoMapperTest {

    @Test
    void estadosAtivosViramEmProcessamento() {
        assertThat(StatusPixPublicoMapper.mapear(StatusPixTransferencia.CRIADA))
                .isEqualTo(StatusPixPublico.EM_PROCESSAMENTO);
        assertThat(StatusPixPublicoMapper.mapear(StatusPixTransferencia.SOLICITADA))
                .isEqualTo(StatusPixPublico.EM_PROCESSAMENTO);
        assertThat(StatusPixPublicoMapper.mapear(StatusPixTransferencia.PROCESSANDO))
                .isEqualTo(StatusPixPublico.EM_PROCESSAMENTO);
    }

    @Test
    void concluidaViraLiquidado() {
        assertThat(StatusPixPublicoMapper.mapear(StatusPixTransferencia.CONCLUIDA))
                .isEqualTo(StatusPixPublico.LIQUIDADO);
    }

    @Test
    void falhouViraFalhou() {
        assertThat(StatusPixPublicoMapper.mapear(StatusPixTransferencia.FALHOU)).isEqualTo(StatusPixPublico.FALHOU);
    }

    @Test
    void canceladaViraCancelado() {
        assertThat(StatusPixPublicoMapper.mapear(StatusPixTransferencia.CANCELADA))
                .isEqualTo(StatusPixPublico.CANCELADO);
    }

    @Test
    void cobreTodosOsEstadosInternos() {
        // Guarda contra novo estado interno sem mapeamento: o mapper nunca deve retornar null.
        for (StatusPixTransferencia status : StatusPixTransferencia.values()) {
            assertThat(StatusPixPublicoMapper.mapear(status)).isNotNull();
        }
    }
}
