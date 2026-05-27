package com.dynamis.sep_api.backoffice.domain.vo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StatusItemFilaTest {

    @Test
    void ativosEFinais_disjuntos() {
        for (StatusItemFila s : StatusItemFila.values()) {
            assertThat(s.isAtivo() ^ s.isFinal())
                    .as("%s deve ser ativo OU final, exclusivamente", s)
                    .isTrue();
        }
    }

    @Test
    void permiteAssumir_apenasAberto() {
        assertThat(StatusItemFila.ABERTO.permiteAssumir()).isTrue();
        assertThat(StatusItemFila.EM_TRATAMENTO.permiteAssumir()).isFalse();
        assertThat(StatusItemFila.RESOLVIDO.permiteAssumir()).isFalse();
        assertThat(StatusItemFila.IGNORADO.permiteAssumir()).isFalse();
    }

    @Test
    void permiteResolver_apenasEmTratamento() {
        assertThat(StatusItemFila.EM_TRATAMENTO.permiteResolver()).isTrue();
        for (StatusItemFila s : StatusItemFila.values()) {
            if (s == StatusItemFila.EM_TRATAMENTO) continue;
            assertThat(s.permiteResolver())
                    .as("permiteResolver nao deve aceitar %s", s)
                    .isFalse();
        }
    }

    @Test
    void permiteIgnorar_abertoOuEmTratamento() {
        assertThat(StatusItemFila.ABERTO.permiteIgnorar()).isTrue();
        assertThat(StatusItemFila.EM_TRATAMENTO.permiteIgnorar()).isTrue();
        assertThat(StatusItemFila.RESOLVIDO.permiteIgnorar()).isFalse();
        assertThat(StatusItemFila.IGNORADO.permiteIgnorar()).isFalse();
    }
}
