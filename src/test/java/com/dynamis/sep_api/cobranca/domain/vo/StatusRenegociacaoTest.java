package com.dynamis.sep_api.cobranca.domain.vo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StatusRenegociacaoTest {

    @Test
    void isFinal_apenasPropostaNaoEhFinal() {
        assertThat(StatusRenegociacao.PROPOSTA.isFinal()).isFalse();
        assertThat(StatusRenegociacao.ACEITA.isFinal()).isTrue();
        assertThat(StatusRenegociacao.RECUSADA.isFinal()).isTrue();
        assertThat(StatusRenegociacao.EXPIRADA.isFinal()).isTrue();
    }
}
