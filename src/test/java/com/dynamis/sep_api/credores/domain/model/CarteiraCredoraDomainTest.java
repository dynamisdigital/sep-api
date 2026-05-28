package com.dynamis.sep_api.credores.domain.model;

import com.dynamis.sep_api.credores.domain.vo.StatusInteresseCredora;
import com.dynamis.sep_api.credores.domain.vo.StatusOperacaoFinanciada;
import com.dynamis.sep_api.credores.domain.vo.StatusOportunidade;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Testes de dominio das transicoes da carteira credora (Sprint 17 Task 17.1). */
class CarteiraCredoraDomainTest {

    @Test
    void oportunidadeNasceDisponivelEEncerra() {
        OportunidadeInvestimento o = OportunidadeInvestimento.criar(
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("10000.00"), 12, new BigDecimal("0.020000"));

        assertThat(o.getStatus()).isEqualTo(StatusOportunidade.DISPONIVEL);
        assertThat(o.isDisponivel()).isTrue();

        o.encerrar();
        assertThat(o.getStatus()).isEqualTo(StatusOportunidade.ENCERRADA);
        assertThat(o.isDisponivel()).isFalse();
    }

    @Test
    void atualizarSnapshotRefrescaDadosDaProposta() {
        UUID propostaId = UUID.randomUUID();
        OportunidadeInvestimento o =
                OportunidadeInvestimento.criar(propostaId, null, new BigDecimal("5000.00"), 6, null);

        UUID contratoId = UUID.randomUUID();
        o.atualizarSnapshot(contratoId, new BigDecimal("5500.00"), 8, new BigDecimal("0.019000"));

        assertThat(o.getPropostaId()).isEqualTo(propostaId);
        assertThat(o.getContratoId()).isEqualTo(contratoId);
        assertThat(o.getValor()).isEqualByComparingTo("5500.00");
        assertThat(o.getPrazoMeses()).isEqualTo(8);
        assertThat(o.getTaxaJurosMensal()).isEqualByComparingTo("0.019000");
    }

    @Test
    void interesseNasceAtivoECancelaIdempotente() {
        InteresseCredora i = InteresseCredora.registrar(UUID.randomUUID(), UUID.randomUUID());
        assertThat(i.getStatus()).isEqualTo(StatusInteresseCredora.ATIVO);
        assertThat(i.isAtivo()).isTrue();

        i.cancelar();
        assertThat(i.getStatus()).isEqualTo(StatusInteresseCredora.CANCELADO);
        assertThat(i.isAtivo()).isFalse();

        i.cancelar(); // idempotente
        assertThat(i.getStatus()).isEqualTo(StatusInteresseCredora.CANCELADO);
    }

    @Test
    void operacaoFinanciadaNasceAtivaEEncerra() {
        OperacaoFinanciada op = OperacaoFinanciada.associar(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        assertThat(op.getStatus()).isEqualTo(StatusOperacaoFinanciada.ATIVA);

        op.encerrar();
        assertThat(op.getStatus()).isEqualTo(StatusOperacaoFinanciada.ENCERRADA);
    }
}
