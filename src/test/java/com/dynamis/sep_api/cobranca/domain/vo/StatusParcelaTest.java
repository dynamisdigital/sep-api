package com.dynamis.sep_api.cobranca.domain.vo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StatusParcelaTest {

    @Test
    void isFinal_apenasPaga() {
        assertThat(StatusParcela.PAGA.isFinal()).isTrue();
        assertThat(StatusParcela.PENDENTE.isFinal()).isFalse();
        assertThat(StatusParcela.PARCIALMENTE_PAGA.isFinal()).isFalse();
        assertThat(StatusParcela.ATRASADA.isFinal()).isFalse();
        assertThat(StatusParcela.INADIMPLENTE.isFinal()).isFalse();
    }

    @Test
    void permiteRecebimento_pagaRejeita() {
        assertThat(StatusParcela.PAGA.permiteRecebimento()).isFalse();
        assertThat(StatusParcela.PENDENTE.permiteRecebimento()).isTrue();
        assertThat(StatusParcela.PARCIALMENTE_PAGA.permiteRecebimento()).isTrue();
        assertThat(StatusParcela.ATRASADA.permiteRecebimento()).isTrue();
        assertThat(StatusParcela.INADIMPLENTE.permiteRecebimento()).isTrue();
    }

    @Test
    void permiteMarcarAtrasada_apenasPendente() {
        assertThat(StatusParcela.PENDENTE.permiteMarcarAtrasada()).isTrue();
        assertThat(StatusParcela.ATRASADA.permiteMarcarAtrasada()).isFalse();
        assertThat(StatusParcela.PAGA.permiteMarcarAtrasada()).isFalse();
        assertThat(StatusParcela.PARCIALMENTE_PAGA.permiteMarcarAtrasada()).isFalse();
        assertThat(StatusParcela.INADIMPLENTE.permiteMarcarAtrasada()).isFalse();
    }
}
