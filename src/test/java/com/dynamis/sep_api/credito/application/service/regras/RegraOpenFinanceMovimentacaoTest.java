package com.dynamis.sep_api.credito.application.service.regras;

import com.dynamis.sep_api.credito.application.service.MotorTestFixtures;
import com.dynamis.sep_api.credito.application.service.dto.ContextoAvaliacaoCredito;
import com.dynamis.sep_api.credito.application.service.dto.RegraResultado;
import com.dynamis.sep_api.credito.domain.model.MovimentacaoOpenFinance;
import com.dynamis.sep_api.credito.domain.model.PropostaCredito;
import com.dynamis.sep_api.credito.domain.vo.ResultadoRegra;
import com.dynamis.sep_api.onboarding.domain.vo.StatusOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.TipoSolicitante;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RegraOpenFinanceMovimentacaoTest {

    private final RegraOpenFinanceMovimentacao regra = new RegraOpenFinanceMovimentacao();

    @Test
    void semSnapshotRetornaPassouNeutro() {
        // Open Finance opt-in: ausencia nao penaliza score.
        PropostaCredito p = MotorTestFixtures.propostaPj(new BigDecimal("12000"), 12);
        RegraResultado r = regra.avaliar(MotorTestFixtures.contextoPjOk(p));
        assertThat(r.resultado()).isEqualTo(ResultadoRegra.PASSOU);
        assertThat(r.ajusteScore()).isZero();
    }

    @Test
    void entradasMaiorIgual3xParcelaConcedeBonusForte() {
        // valor=12000 / prazo=12 => parcela = 1000; entradas=3000 (3x) -> bonus forte 200
        ContextoAvaliacaoCredito ctx = contextoComMovimentacao(
                BigDecimal.valueOf(12000),
                12,
                new BigDecimal("3000.00"),
                new BigDecimal("1000"),
                new BigDecimal("500"),
                6);
        RegraResultado r = regra.avaliar(ctx);
        assertThat(r.resultado()).isEqualTo(ResultadoRegra.PASSOU);
        assertThat(r.ajusteScore()).isEqualTo(RegraOpenFinanceMovimentacao.BONUS_FORTE);
    }

    @Test
    void entradasMaiorIgual1xParcelaConcedeBonusParcial() {
        // parcela=1000; entradas=1500 (1.5x) -> bonus parcial 100
        ContextoAvaliacaoCredito ctx = contextoComMovimentacao(
                BigDecimal.valueOf(12000),
                12,
                new BigDecimal("1500.00"),
                new BigDecimal("800"),
                new BigDecimal("400"),
                6);
        RegraResultado r = regra.avaliar(ctx);
        assertThat(r.resultado()).isEqualTo(ResultadoRegra.PASSOU);
        assertThat(r.ajusteScore()).isEqualTo(RegraOpenFinanceMovimentacao.BONUS_PARCIAL);
    }

    @Test
    void entradasMenorQueParcelaFalhaSemAjusteExtra() {
        // parcela=1000; entradas=500 -> falha leve, ajuste 0 (motor aplica penalidade padrao)
        ContextoAvaliacaoCredito ctx = contextoComMovimentacao(
                BigDecimal.valueOf(12000),
                12,
                new BigDecimal("500.00"),
                new BigDecimal("300"),
                new BigDecimal("200"),
                6);
        RegraResultado r = regra.avaliar(ctx);
        assertThat(r.resultado()).isEqualTo(ResultadoRegra.FALHOU);
        assertThat(r.ajusteScore()).isZero();
        assertThat(r.motivo()).contains("parcela");
    }

    @Test
    void saldoMedioNegativoFalhaComPenalidadeExtra() {
        // saldo negativo dispara antes do check de entradas
        ContextoAvaliacaoCredito ctx = contextoComMovimentacao(
                BigDecimal.valueOf(12000),
                12,
                new BigDecimal("5000.00"),
                new BigDecimal("6000"),
                new BigDecimal("-500"),
                6);
        RegraResultado r = regra.avaliar(ctx);
        assertThat(r.resultado()).isEqualTo(ResultadoRegra.FALHOU);
        assertThat(r.ajusteScore()).isEqualTo(-RegraOpenFinanceMovimentacao.PENALIDADE_SALDO_NEGATIVO);
        assertThat(r.motivo()).contains("Saldo medio negativo");
    }

    @Test
    void entradasNullRetornaPendente() {
        ContextoAvaliacaoCredito ctx = contextoComMovimentacao(
                BigDecimal.valueOf(12000), 12, null, new BigDecimal("500"), new BigDecimal("100"), 6);
        RegraResultado r = regra.avaliar(ctx);
        assertThat(r.resultado()).isEqualTo(ResultadoRegra.PENDENTE);
    }

    private ContextoAvaliacaoCredito contextoComMovimentacao(
            BigDecimal valorProposta,
            int prazoMeses,
            BigDecimal mediaEntradas,
            BigDecimal mediaSaidas,
            BigDecimal saldoMedio,
            int mesesAvaliados) {
        PropostaCredito p = MotorTestFixtures.propostaPj(valorProposta, prazoMeses);
        MovimentacaoOpenFinance mov = MovimentacaoOpenFinance.registrar(
                UUID.randomUUID(), p.getId(), "{}", mediaEntradas, mediaSaidas, saldoMedio, mesesAvaliados);
        return new ContextoAvaliacaoCredito(
                p,
                TipoSolicitante.EMPRESA,
                StatusOnboarding.APROVADO_FINAL,
                null,
                LocalDate.now().minusYears(2),
                mov);
    }
}
