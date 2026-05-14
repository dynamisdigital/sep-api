package com.dynamis.sep_api.onboarding.domain.model;

import com.dynamis.sep_api.onboarding.domain.exception.StatusOnboardingInvalidoException;
import com.dynamis.sep_api.onboarding.domain.vo.StatusOnboarding;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResultadoVerificacaoTest {

    @Test
    void registrarAceitaApenasStatusFinaisAPROVADO() {
        ResultadoVerificacao r =
                ResultadoVerificacao.registrar(UUID.randomUUID(), StatusOnboarding.APROVADO, null, "{\"ok\":true}");

        assertThat(r.getStatusFinal()).isEqualTo(StatusOnboarding.APROVADO);
        assertThat(r.getId()).isNotNull();
        assertThat(r.getDataResultado()).isNotNull();
    }

    @ParameterizedTest
    @EnumSource(
            value = StatusOnboarding.class,
            mode = Mode.EXCLUDE,
            names = {"APROVADO", "REPROVADO", "PENDENCIA"})
    void registrarRejeitaStatusNaoFinalDeKycKyb(StatusOnboarding statusInvalido) {
        assertThatThrownBy(() -> ResultadoVerificacao.registrar(UUID.randomUUID(), statusInvalido, null, "{}"))
                .isInstanceOf(StatusOnboardingInvalidoException.class);
    }

    @Test
    void registrarRejeitaStatusNulo() {
        assertThatThrownBy(() -> ResultadoVerificacao.registrar(UUID.randomUUID(), null, null, "{}"))
                .isInstanceOf(StatusOnboardingInvalidoException.class)
                .hasMessageContaining("obrigatorio");
    }
}
