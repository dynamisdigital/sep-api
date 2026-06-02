package com.dynamis.sep_api.pix.domain.model;

import com.dynamis.sep_api.pix.domain.vo.StatusPixRecebimento;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PixRecebimentoConciliacaoTest {

    private PixRecebimento recebido() {
        return PixRecebimento.registrar("e2e-1", new BigDecimal("500.00"), OffsetDateTime.now(), "corr-1");
    }

    @Test
    void conciliar_vinculaEMarcaConciliado() {
        PixRecebimento r = recebido();
        UUID referenciaId = UUID.randomUUID();
        UUID parcelaId = UUID.randomUUID();
        UUID recebimentoCobrancaId = UUID.randomUUID();

        r.conciliar(referenciaId, parcelaId, recebimentoCobrancaId);

        assertThat(r.getStatus()).isEqualTo(StatusPixRecebimento.CONCILIADO);
        assertThat(r.getReferenciaId()).isEqualTo(referenciaId);
        assertThat(r.getParcelaId()).isEqualTo(parcelaId);
        assertThat(r.getRecebimentoCobrancaId()).isEqualTo(recebimentoCobrancaId);
    }

    @Test
    void registrarDivergencia_guardaMotivoEMarcaNaoIdentificado() {
        PixRecebimento r = recebido();

        r.registrarDivergencia(null, null, "referencia desconhecida");

        assertThat(r.getStatus()).isEqualTo(StatusPixRecebimento.NAO_IDENTIFICADO);
        assertThat(r.getMotivoDivergencia()).isEqualTo("referencia desconhecida");
    }

    @Test
    void registrarDivergencia_truncaMotivoEm240() {
        PixRecebimento r = recebido();

        r.registrarDivergencia(null, null, "x".repeat(300));

        assertThat(r.getMotivoDivergencia()).hasSize(240);
    }

    @Test
    void marcarFalhouComMotivo() {
        PixRecebimento r = recebido();

        r.marcarFalhou("erro tecnico na baixa");

        assertThat(r.getStatus()).isEqualTo(StatusPixRecebimento.FALHOU);
        assertThat(r.getMotivoDivergencia()).isEqualTo("erro tecnico na baixa");
    }
}
