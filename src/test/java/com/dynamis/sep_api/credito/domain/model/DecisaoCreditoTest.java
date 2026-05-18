package com.dynamis.sep_api.credito.domain.model;

import com.dynamis.sep_api.credito.domain.vo.OrigemDecisao;
import com.dynamis.sep_api.credito.domain.vo.StatusProposta;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DecisaoCreditoTest {

    @Test
    void decisaoPorMotorSomenteRejeitada() {
        UUID propostaId = UUID.randomUUID();
        DecisaoCredito d = DecisaoCredito.porMotor(propostaId, StatusProposta.REJEITADA, 250);

        assertThat(d.getOrigem()).isEqualTo(OrigemDecisao.MOTOR);
        assertThat(d.getStatusFinal()).isEqualTo(StatusProposta.REJEITADA);
        assertThat(d.getScoreMotor()).isEqualTo(250);
        assertThat(d.getParecerId()).isNull();
    }

    @Test
    void decisaoPorMotorRejeitaAprovada() {
        assertThatThrownBy(() -> DecisaoCredito.porMotor(UUID.randomUUID(), StatusProposta.APROVADA, 900))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decisaoPorParecerExigeStatusFinal() {
        UUID propostaId = UUID.randomUUID();
        UUID parecerId = UUID.randomUUID();
        DecisaoCredito d = DecisaoCredito.porParecer(propostaId, StatusProposta.APROVADA, 850, parecerId);

        assertThat(d.getOrigem()).isEqualTo(OrigemDecisao.MANUAL);
        assertThat(d.getStatusFinal()).isEqualTo(StatusProposta.APROVADA);
        assertThat(d.getParecerId()).isEqualTo(parecerId);
        assertThat(d.getScoreMotor()).isEqualTo(850);
    }

    @Test
    void decisaoPorParecerRejeitaStatusNaoFinal() {
        assertThatThrownBy(() ->
                        DecisaoCredito.porParecer(UUID.randomUUID(), StatusProposta.EM_ANALISE, 500, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decisaoPorParecerExigeParecerId() {
        assertThatThrownBy(() -> DecisaoCredito.porParecer(UUID.randomUUID(), StatusProposta.APROVADA, 850, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
