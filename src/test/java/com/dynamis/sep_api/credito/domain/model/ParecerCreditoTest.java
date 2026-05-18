package com.dynamis.sep_api.credito.domain.model;

import com.dynamis.sep_api.credito.domain.exception.PropostaInvalidaException;
import com.dynamis.sep_api.credito.domain.vo.DecisaoParecer;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParecerCreditoTest {

    private static final UUID PROPOSTA = UUID.randomUUID();
    private static final UUID PARECERISTA = UUID.randomUUID();
    private static final String JUSTIFICATIVA_OK = "Cliente com bom historico interno";

    @Test
    void registrarParecerPreservaCampos() {
        ParecerCredito p =
                ParecerCredito.registrar(PROPOSTA, PARECERISTA, DecisaoParecer.APROVAR, JUSTIFICATIVA_OK, 850, 1);

        assertThat(p.getId()).isNotNull();
        assertThat(p.getPropostaId()).isEqualTo(PROPOSTA);
        assertThat(p.getPareceristaId()).isEqualTo(PARECERISTA);
        assertThat(p.getDecisao()).isEqualTo(DecisaoParecer.APROVAR);
        assertThat(p.getJustificativa()).isEqualTo(JUSTIFICATIVA_OK);
        assertThat(p.getScoreMotorSnapshot()).isEqualTo(850);
        assertThat(p.getVersao()).isEqualTo(1);
        assertThat(p.getDataParecer()).isNotNull();
    }

    @Test
    void justificativaVaziaRejeitada() {
        assertThatThrownBy(() -> ParecerCredito.registrar(PROPOSTA, PARECERISTA, DecisaoParecer.APROVAR, "", 800, 1))
                .isInstanceOf(PropostaInvalidaException.class);
        assertThatThrownBy(() -> ParecerCredito.registrar(PROPOSTA, PARECERISTA, DecisaoParecer.APROVAR, "   ", 800, 1))
                .isInstanceOf(PropostaInvalidaException.class);
    }

    @Test
    void justificativaCurtaRejeitada() {
        assertThatThrownBy(() -> ParecerCredito.registrar(PROPOSTA, PARECERISTA, DecisaoParecer.APROVAR, "ok", 800, 1))
                .isInstanceOf(PropostaInvalidaException.class)
                .hasMessageContaining("pelo menos");
    }

    @Test
    void justificativaMuitoLongaRejeitada() {
        String muitoLonga = "a".repeat(1001);
        assertThatThrownBy(() ->
                        ParecerCredito.registrar(PROPOSTA, PARECERISTA, DecisaoParecer.APROVAR, muitoLonga, 800, 1))
                .isInstanceOf(PropostaInvalidaException.class);
    }

    @Test
    void versaoZeroRejeitada() {
        assertThatThrownBy(() -> ParecerCredito.registrar(
                        PROPOSTA, PARECERISTA, DecisaoParecer.APROVAR, JUSTIFICATIVA_OK, 800, 0))
                .isInstanceOf(PropostaInvalidaException.class);
    }

    @Test
    void scoreSnapshotNuloAceito() {
        ParecerCredito p =
                ParecerCredito.registrar(PROPOSTA, PARECERISTA, DecisaoParecer.PENDENCIA, JUSTIFICATIVA_OK, null, 1);
        assertThat(p.getScoreMotorSnapshot()).isNull();
    }

    @Test
    void justificativaSofreTrim() {
        ParecerCredito p = ParecerCredito.registrar(
                PROPOSTA, PARECERISTA, DecisaoParecer.APROVAR, "   " + JUSTIFICATIVA_OK + "   ", 800, 1);
        assertThat(p.getJustificativa()).isEqualTo(JUSTIFICATIVA_OK);
    }
}
