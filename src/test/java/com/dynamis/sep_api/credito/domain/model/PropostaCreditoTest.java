package com.dynamis.sep_api.credito.domain.model;

import com.dynamis.sep_api.credito.domain.exception.PropostaInvalidaException;
import com.dynamis.sep_api.credito.domain.exception.StatusPropostaInvalidoException;
import com.dynamis.sep_api.credito.domain.vo.DecisaoParecer;
import com.dynamis.sep_api.credito.domain.vo.Money;
import com.dynamis.sep_api.credito.domain.vo.StatusProposta;
import com.dynamis.sep_api.credito.domain.vo.TipoOperacao;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PropostaCreditoTest {

    private static final UUID TOMADOR = UUID.randomUUID();
    private static final UUID ONBOARDING = UUID.randomUUID();

    @Test
    void criarPropostaInicializaEmAnalise() {
        PropostaCredito p = novaProposta();

        assertThat(p.getId()).isNotNull();
        assertThat(p.getTomadorId()).isEqualTo(TOMADOR);
        assertThat(p.getSolicitacaoOnboardingId()).isEqualTo(ONBOARDING);
        assertThat(p.getStatus()).isEqualTo(StatusProposta.EM_ANALISE);
        assertThat(p.getMoeda()).isEqualTo("BRL");
        assertThat(p.getPrazoMeses()).isEqualTo(12);
        assertThat(p.getTipoOperacao()).isEqualTo(TipoOperacao.CAPITAL_GIRO);
    }

    @Test
    void prazoZeroOuNegativoRejeitado() {
        assertThatThrownBy(() ->
                        PropostaCredito.criar(TOMADOR, ONBOARDING, TipoOperacao.CAPITAL_GIRO, Money.brl("10000"), 0))
                .isInstanceOf(PropostaInvalidaException.class)
                .hasMessageContaining("prazoMeses");
        assertThatThrownBy(() ->
                        PropostaCredito.criar(TOMADOR, ONBOARDING, TipoOperacao.CAPITAL_GIRO, Money.brl("10000"), -3))
                .isInstanceOf(PropostaInvalidaException.class);
    }

    @Test
    void criarComTomadorNuloRejeitado() {
        assertThatThrownBy(() -> PropostaCredito.criar(null, ONBOARDING, TipoOperacao.OUTROS, Money.brl("100"), 6))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void aplicarSugestaoMotorPreAprovada() {
        PropostaCredito p = novaProposta();
        p.aplicarSugestaoMotor(StatusProposta.PRE_APROVADA);
        assertThat(p.getStatus()).isEqualTo(StatusProposta.PRE_APROVADA);
    }

    @Test
    void aplicarSugestaoMotorRejeitada() {
        PropostaCredito p = novaProposta();
        p.aplicarSugestaoMotor(StatusProposta.REJEITADA);
        assertThat(p.getStatus()).isEqualTo(StatusProposta.REJEITADA);
        assertThat(p.getStatus().isFinal()).isTrue();
    }

    @Test
    void aplicarSugestaoMotorNaoAceitaStatusInvalido() {
        PropostaCredito p = novaProposta();
        assertThatThrownBy(() -> p.aplicarSugestaoMotor(StatusProposta.APROVADA))
                .isInstanceOf(StatusPropostaInvalidoException.class);
        assertThatThrownBy(() -> p.aplicarSugestaoMotor(StatusProposta.PENDENCIA))
                .isInstanceOf(StatusPropostaInvalidoException.class);
    }

    @Test
    void aplicarSugestaoMotorBloqueadoAposPreAprovada() {
        PropostaCredito p = novaProposta();
        p.aplicarSugestaoMotor(StatusProposta.PRE_APROVADA);
        assertThatThrownBy(() -> p.aplicarSugestaoMotor(StatusProposta.REJEITADA))
                .isInstanceOf(StatusPropostaInvalidoException.class);
    }

    @Test
    void registrarDecisaoAprovarAPartirDePreAprovada() {
        PropostaCredito p = novaProposta();
        p.aplicarSugestaoMotor(StatusProposta.PRE_APROVADA);
        p.registrarDecisaoManual(DecisaoParecer.APROVAR);
        assertThat(p.getStatus()).isEqualTo(StatusProposta.APROVADA);
    }

    @Test
    void registrarDecisaoRejeitarAPartirDeEmAnalise() {
        PropostaCredito p = novaProposta();
        p.registrarDecisaoManual(DecisaoParecer.REJEITAR);
        assertThat(p.getStatus()).isEqualTo(StatusProposta.REJEITADA);
    }

    @Test
    void registrarDecisaoAprovarBloqueiaAposEstadoFinal() {
        PropostaCredito p = novaProposta();
        p.registrarDecisaoManual(DecisaoParecer.REJEITAR);
        assertThatThrownBy(() -> p.registrarDecisaoManual(DecisaoParecer.APROVAR))
                .isInstanceOf(StatusPropostaInvalidoException.class);
    }

    @Test
    void registrarDecisaoPendenciaTransitaParaPENDENCIA() {
        PropostaCredito p = novaProposta();
        p.registrarDecisaoManual(DecisaoParecer.PENDENCIA);
        assertThat(p.getStatus()).isEqualTo(StatusProposta.PENDENCIA);
    }

    @Test
    void registrarDecisaoAprovarFuncionaAPartirDePENDENCIA() {
        PropostaCredito p = novaProposta();
        p.registrarDecisaoManual(DecisaoParecer.PENDENCIA);
        p.registrarDecisaoManual(DecisaoParecer.APROVAR);
        assertThat(p.getStatus()).isEqualTo(StatusProposta.APROVADA);
    }

    @Test
    void marcarPendenciaAceitavelEmEmAnaliseEPreAprovada() {
        PropostaCredito p1 = novaProposta();
        p1.marcarPendencia();
        assertThat(p1.getStatus()).isEqualTo(StatusProposta.PENDENCIA);

        PropostaCredito p2 = novaProposta();
        p2.aplicarSugestaoMotor(StatusProposta.PRE_APROVADA);
        p2.marcarPendencia();
        assertThat(p2.getStatus()).isEqualTo(StatusProposta.PENDENCIA);
    }

    @Test
    void marcarPendenciaBloqueadaEmEstadoFinalOuPendencia() {
        PropostaCredito p = novaProposta();
        p.registrarDecisaoManual(DecisaoParecer.REJEITAR);
        assertThatThrownBy(p::marcarPendencia).isInstanceOf(StatusPropostaInvalidoException.class);

        PropostaCredito p2 = novaProposta();
        p2.marcarPendencia();
        assertThatThrownBy(p2::marcarPendencia).isInstanceOf(StatusPropostaInvalidoException.class);
    }

    @Test
    void statusFinalEhFinal() {
        assertThat(StatusProposta.APROVADA.isFinal()).isTrue();
        assertThat(StatusProposta.REJEITADA.isFinal()).isTrue();
        assertThat(StatusProposta.EM_ANALISE.isFinal()).isFalse();
        assertThat(StatusProposta.PRE_APROVADA.isFinal()).isFalse();
        assertThat(StatusProposta.PENDENCIA.isFinal()).isFalse();
    }

    private PropostaCredito novaProposta() {
        return PropostaCredito.criar(TOMADOR, ONBOARDING, TipoOperacao.CAPITAL_GIRO, Money.brl("10000.00"), 12);
    }
}
