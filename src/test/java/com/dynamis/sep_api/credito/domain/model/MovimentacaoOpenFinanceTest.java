package com.dynamis.sep_api.credito.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MovimentacaoOpenFinanceTest {

    @Test
    void registrarPersisteSnapshotComDataAtual() {
        UUID cons = UUID.randomUUID();
        UUID prop = UUID.randomUUID();
        MovimentacaoOpenFinance m = MovimentacaoOpenFinance.registrar(
                cons,
                prop,
                "{\"meses\":6}",
                new BigDecimal("10000.00"),
                new BigDecimal("7000.00"),
                new BigDecimal("3000.00"),
                6);

        assertThat(m.getId()).isNotNull();
        assertThat(m.getConsentimentoId()).isEqualTo(cons);
        assertThat(m.getPropostaId()).isEqualTo(prop);
        assertThat(m.getPayloadConsolidado()).contains("meses");
        assertThat(m.getMediaEntradasMensal()).isEqualByComparingTo("10000.00");
        assertThat(m.getMediaSaidasMensal()).isEqualByComparingTo("7000.00");
        assertThat(m.getSaldoMedio()).isEqualByComparingTo("3000.00");
        assertThat(m.getNumeroMesesAvaliados()).isEqualTo(6);
        assertThat(m.getDataRecebimento()).isNotNull();
    }

    @Test
    void payloadObrigatorio() {
        assertThatThrownBy(() -> MovimentacaoOpenFinance.registrar(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        null,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        0))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void consentimentoObrigatorio() {
        assertThatThrownBy(() -> MovimentacaoOpenFinance.registrar(
                        null, UUID.randomUUID(), "{}", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0))
                .isInstanceOf(NullPointerException.class);
    }
}
