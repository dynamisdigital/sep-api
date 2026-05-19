package com.dynamis.sep_api.credito.application.service.regras;

import com.dynamis.sep_api.credito.application.service.RegraCredito;
import com.dynamis.sep_api.credito.application.service.dto.ContextoAvaliacaoCredito;
import com.dynamis.sep_api.credito.application.service.dto.RegraResultado;
import com.dynamis.sep_api.credito.domain.model.MovimentacaoOpenFinance;
import com.dynamis.sep_api.credito.domain.model.PropostaCredito;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Avalia dados de movimentacao bancaria via Open Finance Brasil (Sprint 9 Task 9.4). Bonifica
 * score quando entradas mensais cobrem a parcela estimada da proposta com folga; penaliza saldo
 * medio negativo recorrente.
 *
 * <p>Sem snapshot de movimentacao no contexto (fluxo Sprint 8 ou tomador sem consentimento Open
 * Finance) → {@code PASSOU} sem bonus (regra inaplicavel — Open Finance e opt-in). NAO usamos
 * {@code PENDENTE} porque o motor penaliza pendencias; aqui a ausencia e esperada quando o
 * tomador nao optou pelo fluxo.
 *
 * <p>Cenarios com snapshot:
 *
 * <ul>
 *   <li>{@code mediaEntradasMensal >= 3x parcela estimada} → {@code PASSOU} +200 (bonus forte);
 *   <li>{@code mediaEntradasMensal >= 1x parcela estimada} → {@code PASSOU} +100 (bonus parcial);
 *   <li>{@code mediaEntradasMensal <  1x parcela estimada} → {@code FALHOU} sem penalidade extra
 *       (motor aplica penalidade padrao);
 *   <li>{@code saldoMedio &lt; 0} → {@code FALHOU} com penalidade extra {@code -150} (alerta forte
 *       de cheque especial recorrente).
 * </ul>
 *
 * <p>Parcela estimada = {@code valorSolicitado / prazoMeses} (sem juros — calculo financeiro real
 * fora de escopo ate Sprint 10+).
 */
@Component
public class RegraOpenFinanceMovimentacao implements RegraCredito {

    public static final String NOME = "open-finance-movimentacao";

    static final int BONUS_FORTE = 200;
    static final int BONUS_PARCIAL = 100;
    static final int PENALIDADE_SALDO_NEGATIVO = 150;

    @Override
    public String nome() {
        return NOME;
    }

    @Override
    public RegraResultado avaliar(ContextoAvaliacaoCredito contexto) {
        MovimentacaoOpenFinance movimentacao = contexto.movimentacaoOpenFinance();
        if (movimentacao == null) {
            // Open Finance e opt-in — ausencia nao penaliza score (passou neutro).
            return RegraResultado.passou(NOME);
        }
        if (movimentacao.getSaldoMedio() != null && movimentacao.getSaldoMedio().signum() < 0) {
            return RegraResultado.falhouComPenalidadeExtra(
                    NOME,
                    "Saldo medio negativo (cheque especial recorrente): " + movimentacao.getSaldoMedio(),
                    PENALIDADE_SALDO_NEGATIVO);
        }

        BigDecimal entradas = movimentacao.getMediaEntradasMensal();
        if (entradas == null) {
            return RegraResultado.pendente(NOME, "media_entradas_mensal ausente no snapshot");
        }
        BigDecimal parcelaEstimada = parcelaEstimada(contexto.proposta());
        if (parcelaEstimada.signum() <= 0) {
            return RegraResultado.pendente(NOME, "Parcela estimada nao calculavel");
        }

        BigDecimal ratio = entradas.divide(parcelaEstimada, 4, RoundingMode.HALF_UP);
        if (ratio.compareTo(BigDecimal.valueOf(3)) >= 0) {
            return RegraResultado.passouComBonus(NOME, BONUS_FORTE);
        }
        if (ratio.compareTo(BigDecimal.ONE) >= 0) {
            return RegraResultado.passouComBonus(NOME, BONUS_PARCIAL);
        }
        return RegraResultado.falhou(
                NOME, "Media de entradas R$ " + entradas + " < parcela estimada R$ " + parcelaEstimada);
    }

    private BigDecimal parcelaEstimada(PropostaCredito proposta) {
        if (proposta.getPrazoMeses() <= 0) {
            return BigDecimal.ZERO;
        }
        return proposta.getValorSolicitado()
                .divide(BigDecimal.valueOf(proposta.getPrazoMeses()), 2, RoundingMode.HALF_UP);
    }
}
