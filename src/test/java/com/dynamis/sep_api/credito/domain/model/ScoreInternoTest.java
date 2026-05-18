package com.dynamis.sep_api.credito.domain.model;

import com.dynamis.sep_api.credito.domain.vo.StatusProposta;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScoreInternoTest {

    @Test
    void calculadoPersisteCamposEDataAtual() {
        UUID propostaId = UUID.randomUUID();
        ScoreInterno s = ScoreInterno.calculado(propostaId, 850, StatusProposta.PRE_APROVADA, 1, 2);

        assertThat(s.getId()).isNotNull();
        assertThat(s.getPropostaId()).isEqualTo(propostaId);
        assertThat(s.getValor()).isEqualTo(850);
        assertThat(s.getStatusSugerido()).isEqualTo(StatusProposta.PRE_APROVADA);
        assertThat(s.getFalhas()).isEqualTo(1);
        assertThat(s.getPendencias()).isEqualTo(2);
        assertThat(s.getDataCalculo()).isNotNull();
    }

    @Test
    void valorAbaixoDeZeroRejeitado() {
        assertThatThrownBy(() -> ScoreInterno.calculado(UUID.randomUUID(), -1, StatusProposta.REJEITADA, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void valorAcimaDe1000Rejeitado() {
        assertThatThrownBy(() -> ScoreInterno.calculado(UUID.randomUUID(), 1001, StatusProposta.REJEITADA, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
