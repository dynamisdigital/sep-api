package com.dynamis.sep_api.cobranca.domain.vo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StatusParcelaTest {

    @Test
    void isFinal_pagaERenegociadaSaoFinais() {
        assertThat(StatusParcela.PAGA.isFinal()).isTrue();
        assertThat(StatusParcela.RENEGOCIADA.isFinal()).isTrue();
        assertThat(StatusParcela.PENDENTE.isFinal()).isFalse();
        assertThat(StatusParcela.PARCIALMENTE_PAGA.isFinal()).isFalse();
        assertThat(StatusParcela.ATRASADA.isFinal()).isFalse();
        assertThat(StatusParcela.INADIMPLENTE.isFinal()).isFalse();
        assertThat(StatusParcela.EM_NEGOCIACAO.isFinal()).isFalse();
    }

    @Test
    void permiteRecebimento_apenasPendenteParcialAtrasada() {
        assertThat(StatusParcela.PENDENTE.permiteRecebimento()).isTrue();
        assertThat(StatusParcela.PARCIALMENTE_PAGA.permiteRecebimento()).isTrue();
        assertThat(StatusParcela.ATRASADA.permiteRecebimento()).isTrue();
        assertThat(StatusParcela.PAGA.permiteRecebimento()).isFalse();
        assertThat(StatusParcela.INADIMPLENTE.permiteRecebimento()).isFalse();
        assertThat(StatusParcela.EM_NEGOCIACAO.permiteRecebimento()).isFalse();
        assertThat(StatusParcela.RENEGOCIADA.permiteRecebimento()).isFalse();
    }

    @Test
    void permiteMarcarAtrasada_apenasPendente() {
        assertThat(StatusParcela.PENDENTE.permiteMarcarAtrasada()).isTrue();
        for (StatusParcela s : StatusParcela.values()) {
            if (s == StatusParcela.PENDENTE) continue;
            assertThat(s.permiteMarcarAtrasada())
                    .as("permiteMarcarAtrasada nao deve aceitar %s", s)
                    .isFalse();
        }
    }

    @Test
    void permiteMarcarInadimplente_apenasAtrasada() {
        assertThat(StatusParcela.ATRASADA.permiteMarcarInadimplente()).isTrue();
        for (StatusParcela s : StatusParcela.values()) {
            if (s == StatusParcela.ATRASADA) continue;
            assertThat(s.permiteMarcarInadimplente())
                    .as("permiteMarcarInadimplente nao deve aceitar %s", s)
                    .isFalse();
        }
    }

    @Test
    void permiteIniciarRenegociacao_apenasAtrasadaEInadimplente() {
        assertThat(StatusParcela.ATRASADA.permiteIniciarRenegociacao()).isTrue();
        assertThat(StatusParcela.INADIMPLENTE.permiteIniciarRenegociacao()).isTrue();
        assertThat(StatusParcela.PENDENTE.permiteIniciarRenegociacao()).isFalse();
        assertThat(StatusParcela.PARCIALMENTE_PAGA.permiteIniciarRenegociacao()).isFalse();
        assertThat(StatusParcela.PAGA.permiteIniciarRenegociacao()).isFalse();
        assertThat(StatusParcela.EM_NEGOCIACAO.permiteIniciarRenegociacao()).isFalse();
        assertThat(StatusParcela.RENEGOCIADA.permiteIniciarRenegociacao()).isFalse();
    }
}
