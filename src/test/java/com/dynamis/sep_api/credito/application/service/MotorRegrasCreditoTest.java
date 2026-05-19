package com.dynamis.sep_api.credito.application.service;

import com.dynamis.sep_api.credito.application.service.dto.ContextoAvaliacaoCredito;
import com.dynamis.sep_api.credito.application.service.dto.ResultadoAvaliacaoCredito;
import com.dynamis.sep_api.credito.application.service.regras.RegraIdadeMinimaPessoa;
import com.dynamis.sep_api.credito.application.service.regras.RegraOnboardingAprovado;
import com.dynamis.sep_api.credito.application.service.regras.RegraPrazoMaximo;
import com.dynamis.sep_api.credito.application.service.regras.RegraTempoExistenciaEmpresa;
import com.dynamis.sep_api.credito.application.service.regras.RegraValorMaximo;
import com.dynamis.sep_api.credito.domain.model.PropostaCredito;
import com.dynamis.sep_api.credito.domain.vo.StatusProposta;
import com.dynamis.sep_api.onboarding.domain.vo.StatusOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.TipoSolicitante;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MotorRegrasCreditoTest {

    private final CreditoMotorProperties properties = MotorTestFixtures.propertiesDefault();
    private final MotorRegrasCredito motor = new MotorRegrasCredito(
            List.of(
                    new RegraOnboardingAprovado(),
                    new RegraIdadeMinimaPessoa(properties),
                    new RegraTempoExistenciaEmpresa(properties),
                    new RegraValorMaximo(properties),
                    new RegraPrazoMaximo(properties)),
            properties);

    @Test
    void pfFelizSemFalhasGeraScore1000ePreAprovada() {
        PropostaCredito p = MotorTestFixtures.propostaPf(new BigDecimal("10000"), 12);
        ResultadoAvaliacaoCredito r = motor.avaliar(MotorTestFixtures.contextoPfOk(p));

        assertThat(r.score()).isEqualTo(1000);
        assertThat(r.statusSugerido()).isEqualTo(StatusProposta.PRE_APROVADA);
        assertThat(r.falhas()).isZero();
        assertThat(r.pendencias()).isZero();
        assertThat(r.regras()).hasSize(5);
    }

    @Test
    void pjFelizSemFalhasGeraPreAprovada() {
        PropostaCredito p = MotorTestFixtures.propostaPj(new BigDecimal("100000"), 24);
        ResultadoAvaliacaoCredito r = motor.avaliar(MotorTestFixtures.contextoPjOk(p));

        assertThat(r.score()).isEqualTo(1000);
        assertThat(r.statusSugerido()).isEqualTo(StatusProposta.PRE_APROVADA);
    }

    @Test
    void onboardingNaoAprovadoForcaRejeitadaIndependenteDoScore() {
        PropostaCredito p = MotorTestFixtures.propostaPf(new BigDecimal("10000"), 12);
        ContextoAvaliacaoCredito c = new ContextoAvaliacaoCredito(
                p,
                TipoSolicitante.PESSOA,
                StatusOnboarding.APROVADO,
                LocalDate.now().minusYears(30),
                null);
        ResultadoAvaliacaoCredito r = motor.avaliar(c);

        assertThat(r.statusSugerido()).isEqualTo(StatusProposta.REJEITADA);
        assertThat(r.temBloqueioAbsoluto()).isTrue();
        assertThat(r.falhas()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void valorAcimaDoLimitePfReduzScoreEEmAnalise() {
        PropostaCredito p = MotorTestFixtures.propostaPf(new BigDecimal("60000"), 13);
        ResultadoAvaliacaoCredito r = motor.avaliar(MotorTestFixtures.contextoPfOk(p));

        assertThat(r.falhas()).isEqualTo(2);
        assertThat(r.score()).isEqualTo(1000 - 2 * 50);
        assertThat(r.statusSugerido()).isEqualTo(StatusProposta.PRE_APROVADA);
    }

    @Test
    void scoreEntreThresholdsResultaEmAnalise() {
        CreditoMotorProperties p = new CreditoMotorProperties(
                1000, 100, 100, 700, 400, 18, 6, new BigDecimal("50000"), new BigDecimal("200000"), 12, 24);
        MotorRegrasCredito m = new MotorRegrasCredito(
                List.of(
                        new RegraOnboardingAprovado(),
                        new RegraIdadeMinimaPessoa(p),
                        new RegraTempoExistenciaEmpresa(p),
                        new RegraValorMaximo(p),
                        new RegraPrazoMaximo(p)),
                p);
        PropostaCredito prop = MotorTestFixtures.propostaPf(new BigDecimal("60000"), 13);
        ResultadoAvaliacaoCredito r = m.avaliar(MotorTestFixtures.contextoPfOk(prop));
        assertThat(r.score()).isEqualTo(1000 - 2 * 100);
        assertThat(r.statusSugerido()).isEqualTo(StatusProposta.PRE_APROVADA);
    }

    @Test
    void scoreMuitoBaixoResultaRejeitada() {
        CreditoMotorProperties p = new CreditoMotorProperties(
                1000, 400, 400, 700, 400, 18, 6, new BigDecimal("50000"), new BigDecimal("200000"), 12, 24);
        MotorRegrasCredito m = new MotorRegrasCredito(
                List.of(
                        new RegraOnboardingAprovado(),
                        new RegraIdadeMinimaPessoa(p),
                        new RegraTempoExistenciaEmpresa(p),
                        new RegraValorMaximo(p),
                        new RegraPrazoMaximo(p)),
                p);
        PropostaCredito prop = MotorTestFixtures.propostaPf(new BigDecimal("60000"), 13);
        ResultadoAvaliacaoCredito r = m.avaliar(MotorTestFixtures.contextoPfOk(prop));
        assertThat(r.score()).isLessThan(400);
        assertThat(r.statusSugerido()).isEqualTo(StatusProposta.REJEITADA);
    }

    @Test
    void pendenciaSemFalhaReduzScoreMantemEmAnaliseOuPreAprovada() {
        PropostaCredito p = MotorTestFixtures.propostaPj(new BigDecimal("50000"), 24);
        ContextoAvaliacaoCredito c =
                new ContextoAvaliacaoCredito(p, TipoSolicitante.EMPRESA, StatusOnboarding.APROVADO_FINAL, null, null);
        ResultadoAvaliacaoCredito r = motor.avaliar(c);

        assertThat(r.pendencias()).isEqualTo(1);
        assertThat(r.falhas()).isZero();
        assertThat(r.score()).isEqualTo(1000 - 20);
        assertThat(r.statusSugerido()).isEqualTo(StatusProposta.PRE_APROVADA);
    }

    @Test
    void ajusteScorePositivoClampaEm1000() {
        // Sprint 9 fix code review Task 9.4: scoreInicial 1000 + bonus +200 deve clampar em 1000.
        com.dynamis.sep_api.credito.application.service.RegraCredito regraBonus =
                new com.dynamis.sep_api.credito.application.service.RegraCredito() {
                    @Override
                    public String nome() {
                        return "stub-bonus";
                    }

                    @Override
                    public com.dynamis.sep_api.credito.application.service.dto.RegraResultado avaliar(
                            ContextoAvaliacaoCredito ctx) {
                        return com.dynamis.sep_api.credito.application.service.dto.RegraResultado.passouComBonus(
                                "stub-bonus", 500);
                    }
                };
        MotorRegrasCredito m = new MotorRegrasCredito(List.of(regraBonus), properties);
        PropostaCredito p = MotorTestFixtures.propostaPj(new BigDecimal("10000"), 12);
        ResultadoAvaliacaoCredito r = m.avaliar(MotorTestFixtures.contextoPjOk(p));
        assertThat(r.score()).isEqualTo(1000);
    }

    @Test
    void ajusteScoreNegativoExtraSomaPenalidadeFalhaPadrao() {
        // Bonus negativo via falhouComPenalidadeExtra: motor aplica penalidade-falha padrao
        // (50) + penalidade extra (-150) => total -200.
        com.dynamis.sep_api.credito.application.service.RegraCredito regraPenal =
                new com.dynamis.sep_api.credito.application.service.RegraCredito() {
                    @Override
                    public String nome() {
                        return "stub-penalidade";
                    }

                    @Override
                    public com.dynamis.sep_api.credito.application.service.dto.RegraResultado avaliar(
                            ContextoAvaliacaoCredito ctx) {
                        return com.dynamis.sep_api.credito.application.service.dto.RegraResultado
                                .falhouComPenalidadeExtra("stub-penalidade", "alerta", 150);
                    }
                };
        MotorRegrasCredito m = new MotorRegrasCredito(List.of(regraPenal), properties);
        PropostaCredito p = MotorTestFixtures.propostaPj(new BigDecimal("10000"), 12);
        ResultadoAvaliacaoCredito r = m.avaliar(MotorTestFixtures.contextoPjOk(p));
        // scoreInicial 1000 - penalidadeFalha 50 - ajuste 150 = 800
        assertThat(r.score()).isEqualTo(800);
    }

    @Test
    void propertiesInvalidaRejeitada() {
        try {
            new CreditoMotorProperties(
                    0, 50, 20, 700, 400, 18, 6, new BigDecimal("50000"), new BigDecimal("200000"), 12, 24);
            assert false : "deveria ter falhado";
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).contains("scoreInicial");
        }
    }
}
